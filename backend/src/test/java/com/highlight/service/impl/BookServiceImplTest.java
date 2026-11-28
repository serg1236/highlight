package com.highlight.service.impl;

import com.highlight.dto.BookDto;
import com.highlight.entity.*;
import com.highlight.event.BookUploadedEvent;
import com.highlight.exception.BookParseException;
import com.highlight.exception.ResourceNotFoundException;
import com.highlight.exception.UnsupportedBookFormatException;
import com.highlight.parser.BookParser;
import com.highlight.parser.ParsedBook;
import com.highlight.parser.ParsedChapter;
import com.highlight.repository.BookChapterRepository;
import com.highlight.repository.BookContentRepository;
import com.highlight.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

	private static final Long BOOK_ID = 42L;

	private static final byte[] CONTENT = "raw".getBytes();

	@Mock
	private BookParser txtParser;

	@Mock
	private BookParser epubParser;

	@Mock
	private BookRepository bookRepository;

	@Mock
	private BookContentRepository contentRepository;

	@Mock
	private BookChapterRepository chapterRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Captor
	private ArgumentCaptor<BookContent> contentCaptor;

	@Captor
	private ArgumentCaptor<List<BookChapter>> chaptersCaptor;

	private BookServiceImpl service;

	@BeforeEach
	void setUp() {
		when(txtParser.format()).thenReturn(BookFormat.TXT);
		when(epubParser.format()).thenReturn(BookFormat.EPUB);
		service = new BookServiceImpl(List.of(txtParser, epubParser), bookRepository, contentRepository,
				chapterRepository, eventPublisher);
	}

	@Test
	void upload_storesBookChaptersAndPublishesEvent() {
		// given
		var file = new MockMultipartFile("file", "My Tale.TXT", "text/plain", CONTENT);
		var text = "Chapter 1\n\nText.";
		when(txtParser.parse(CONTENT)).thenReturn(new ParsedBook(null, "Jane Writer", "en", text,
				List.of(new ParsedChapter(0, "Chapter 1", 0, text.length()))));
		when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
			Book book = invocation.getArgument(0);
			book.setId(BOOK_ID);
			return book;
		});
		var expected = BookDto.builder()
				.id(BOOK_ID)
				.title("My Tale")
				.author("Jane Writer")
				.originalFilename("My Tale.TXT")
				.format(BookFormat.TXT)
				.language("en")
				.status(BookStatus.UPLOADED)
				.charCount(text.length())
				.chapterCount(1)
				.costUsd(BigDecimal.ZERO)
				.build();

		// when
		var actual = service.upload(file);

		// then
		assertEquals(expected, actual);
		verify(contentRepository).save(contentCaptor.capture());
		assertEquals(BOOK_ID, contentCaptor.getValue().getBookId());
		assertEquals(text, contentCaptor.getValue().getText());
		verify(chapterRepository).saveAll(chaptersCaptor.capture());
		var chapter = chaptersCaptor.getValue().getFirst();
		assertEquals(List.of(BOOK_ID, 0, "Chapter 1", 0, text.length()), List.of(chapter.getBookId(),
				chapter.getChapterIndex(), chapter.getTitle(), chapter.getStartOffset(), chapter.getEndOffset()));
		verify(eventPublisher).publishEvent(new BookUploadedEvent(BOOK_ID));
	}

	@Test
	void upload_parsedTitle_preferredOverFilename() {
		// given
		var file = new MockMultipartFile("file", "book.epub", "application/epub+zip", CONTENT);
		when(epubParser.parse(CONTENT)).thenReturn(new ParsedBook("Real Title", null, null, "Text.",
				List.of(new ParsedChapter(0, null, 0, 5))));
		when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// when
		var actual = service.upload(file);

		// then
		assertEquals("Real Title", actual.getTitle());
		assertEquals(BookFormat.EPUB, actual.getFormat());
	}

	@Test
	void upload_unsupportedExtension_shouldThrowException() {
		var file = new MockMultipartFile("file", "book.pdf", "application/pdf", CONTENT);

		assertThrows(UnsupportedBookFormatException.class, () -> service.upload(file));
		verify(bookRepository, never()).save(any(Book.class));
	}

	@Test
	void upload_missingFilename_shouldThrowException() {
		var file = new MockMultipartFile("file", null, "text/plain", CONTENT);

		assertThrows(UnsupportedBookFormatException.class, () -> service.upload(file));
	}

	@Test
	void upload_parserFails_nothingStored() {
		// given
		var file = new MockMultipartFile("file", "book.txt", "text/plain", CONTENT);
		when(txtParser.parse(CONTENT)).thenThrow(new BookParseException("TXT files must be UTF-8 encoded"));

		// when / then
		assertThrows(BookParseException.class, () -> service.upload(file));
		verify(bookRepository, never()).save(any(Book.class));
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void upload_unreadableFile_shouldThrowException() throws IOException {
		// given
		var file = org.mockito.Mockito.mock(MultipartFile.class);
		when(file.getOriginalFilename()).thenReturn("book.txt");
		when(file.getBytes()).thenThrow(new IOException("disk error"));

		// when / then
		assertThrows(BookParseException.class, () -> service.upload(file));
	}

	@Test
	void getBook_mapsAllFields() {
		// given
		var created = Instant.parse("2026-11-25T10:00:00Z");
		var book = Book.builder().id(BOOK_ID).title("T").author("A").originalFilename("t.fb2")
				.format(BookFormat.FB2).language("uk").status(BookStatus.EXTRACTING_CHARACTERS).charCount(500)
				.chunksTotal(3).chunksDone(1).inputTokens(1000).outputTokens(200).costUsd(new BigDecimal("0.000200"))
				.errorMessage(null).createdAt(created).updatedAt(created).build();
		when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book));
		when(chapterRepository.findAllByBookIdOrderByChapterIndex(BOOK_ID))
				.thenReturn(List.of(new BookChapter(), new BookChapter()));
		var expected = BookDto.builder().id(BOOK_ID).title("T").author("A").originalFilename("t.fb2")
				.format(BookFormat.FB2).language("uk").status(BookStatus.EXTRACTING_CHARACTERS).charCount(500)
				.chapterCount(2).chunksTotal(3).chunksDone(1).inputTokens(1000).outputTokens(200).costUsd(new BigDecimal("0.000200"))
				.createdAt(created).updatedAt(created).build();

		// when
		var actual = service.getBook(BOOK_ID);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void getBook_notFound_shouldThrowException() {
		when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class, () -> service.getBook(BOOK_ID));
	}

	@Test
	void listBooks_newestFirst() {
		// given
		var book = Book.builder().id(BOOK_ID).title("T").originalFilename("t.txt").format(BookFormat.TXT)
				.status(BookStatus.READY).build();
		when(bookRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(book));
		when(chapterRepository.findAllByBookIdOrderByChapterIndex(BOOK_ID)).thenReturn(List.of());
		var expected = List.of(BookDto.builder().id(BOOK_ID).title("T").originalFilename("t.txt")
				.format(BookFormat.TXT).status(BookStatus.READY).costUsd(BigDecimal.ZERO).build());

		// when
		var actual = service.listBooks();

		// then
		assertEquals(expected, actual);
	}

	@Test
	void deleteBook_existing_deletes() {
		when(bookRepository.existsById(BOOK_ID)).thenReturn(true);

		service.deleteBook(BOOK_ID);

		verify(bookRepository).deleteById(BOOK_ID);
	}

	@Test
	void deleteBook_notFound_shouldThrowException() {
		when(bookRepository.existsById(BOOK_ID)).thenReturn(false);

		assertThrows(ResourceNotFoundException.class, () -> service.deleteBook(BOOK_ID));
		verify(bookRepository, never()).deleteById(BOOK_ID);
	}
}
