package com.highlight.service.impl;

import com.highlight.dto.BookDto;
import com.highlight.entity.*;
import com.highlight.event.BookUploadedEvent;
import com.highlight.exception.BookParseException;
import com.highlight.exception.ResourceNotFoundException;
import com.highlight.exception.UnsupportedBookFormatException;
import com.highlight.parser.BookParser;
import com.highlight.parser.ParsedBook;
import com.highlight.repository.BookChapterRepository;
import com.highlight.repository.BookContentRepository;
import com.highlight.repository.BookRepository;
import com.highlight.service.BookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BookServiceImpl implements BookService {

	private final Map<BookFormat, BookParser> parsers;

	private final BookRepository bookRepository;

	private final BookContentRepository contentRepository;

	private final BookChapterRepository chapterRepository;

	private final ApplicationEventPublisher eventPublisher;

	public BookServiceImpl(List<BookParser> parsers, BookRepository bookRepository,
						   BookContentRepository contentRepository, BookChapterRepository chapterRepository,
						   ApplicationEventPublisher eventPublisher) {
		this.parsers = parsers.stream().collect(Collectors.toMap(BookParser::format, Function.identity()));
		this.bookRepository = bookRepository;
		this.contentRepository = contentRepository;
		this.chapterRepository = chapterRepository;
		this.eventPublisher = eventPublisher;
	}

	@Override
	@Transactional
	public BookDto upload(MultipartFile file) {
		var filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		var format = formatOf(filename);
		var parsed = parsers.get(format).parse(readBytes(file));

		var book = bookRepository.save(Book.builder()
				.title(parsed.title() != null ? parsed.title() : stripExtension(filename))
				.author(parsed.author())
				.language(parsed.language())
				.originalFilename(filename)
				.format(format)
				.status(BookStatus.UPLOADED)
				.charCount(parsed.text().length())
				.build());
		contentRepository.save(BookContent.builder().bookId(book.getId()).text(parsed.text()).build());
		chapterRepository.saveAll(toChapters(book.getId(), parsed));
		eventPublisher.publishEvent(new BookUploadedEvent(book.getId()));

		log.info("Book uploaded: bookId={}, file={}, format={}, chars={}, chapters={}", book.getId(), filename,
				format, parsed.text().length(), parsed.chapters().size());
		return toDto(book, parsed.chapters().size());
	}

	@Override
	@Transactional(readOnly = true)
	public List<BookDto> listBooks() {
		return bookRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(book -> toDto(book, chapterCount(book.getId())))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public BookDto getBook(Long bookId) {
		var book = bookRepository.findById(bookId)
				.orElseThrow(() -> new ResourceNotFoundException("Book " + bookId + " not found"));
		return toDto(book, chapterCount(bookId));
	}

	@Override
	@Transactional
	public void deleteBook(Long bookId) {
		if (!bookRepository.existsById(bookId)) {
			throw new ResourceNotFoundException("Book " + bookId + " not found");
		}
		bookRepository.deleteById(bookId);
		log.info("Book deleted: bookId={}", bookId);
	}

	private int chapterCount(Long bookId) {
		return chapterRepository.findAllByBookIdOrderByChapterIndex(bookId).size();
	}

	private static BookFormat formatOf(String filename) {
		var lower = filename.toLowerCase(Locale.ROOT);
		for (var format : BookFormat.values()) {
			if (lower.endsWith("." + format.name().toLowerCase(Locale.ROOT))) {
				return format;
			}
		}
		throw new UnsupportedBookFormatException("Unsupported file '" + filename + "'; expected .epub, .fb2 or .txt");
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new BookParseException("Could not read the uploaded file", e);
		}
	}

	private static String stripExtension(String filename) {
		var dot = filename.lastIndexOf('.');
		return dot > 0 ? filename.substring(0, dot) : filename;
	}

	private static List<BookChapter> toChapters(Long bookId, ParsedBook parsed) {
		return parsed.chapters().stream()
				.map(chapter -> BookChapter.builder()
						.bookId(bookId)
						.chapterIndex(chapter.index())
						.title(chapter.title())
						.startOffset(chapter.startOffset())
						.endOffset(chapter.endOffset())
						.build())
				.toList();
	}

	private static BookDto toDto(Book book, int chapterCount) {
		return BookDto.builder()
				.id(book.getId())
				.title(book.getTitle())
				.author(book.getAuthor())
				.originalFilename(book.getOriginalFilename())
				.format(book.getFormat())
				.language(book.getLanguage())
				.status(book.getStatus())
				.charCount(book.getCharCount())
				.chapterCount(chapterCount)
				.chunksTotal(book.getChunksTotal())
				.chunksDone(book.getChunksDone())
				.inputTokens(book.getInputTokens())
				.outputTokens(book.getOutputTokens())
				.costUsd(book.getCostUsd())
				.errorMessage(book.getErrorMessage())
				.createdAt(book.getCreatedAt())
				.updatedAt(book.getUpdatedAt())
				.build();
	}
}
