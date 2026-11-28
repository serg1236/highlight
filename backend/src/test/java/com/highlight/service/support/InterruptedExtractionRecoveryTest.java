package com.highlight.service.support;

import com.highlight.entity.Book;
import com.highlight.entity.BookStatus;
import com.highlight.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterruptedExtractionRecoveryTest {

	@Mock
	private BookRepository bookRepository;

	@InjectMocks
	private InterruptedExtractionRecovery recovery;

	@Test
	void markInterruptedBooksFailed() {
		// given
		var uploaded = Book.builder().id(1L).status(BookStatus.UPLOADED).build();
		var characters = Book.builder().id(2L).status(BookStatus.EXTRACTING_CHARACTERS).build();
		var scenes = Book.builder().id(3L).status(BookStatus.EXTRACTING_SCENES).build();
		var books = List.of(uploaded, characters, scenes);
		when(bookRepository.findAllByStatusIn(List.of(BookStatus.UPLOADED, BookStatus.EXTRACTING_CHARACTERS,
				BookStatus.EXTRACTING_SCENES))).thenReturn(books);

		// when
		recovery.markInterruptedBooksFailed();

		// then
		verify(bookRepository).saveAll(books);
		assertEquals(List.of(BookStatus.FAILED, BookStatus.FAILED, BookStatus.FAILED),
				List.of(uploaded.getStatus(), characters.getStatus(), scenes.getStatus()));
		var message = InterruptedExtractionRecovery.INTERRUPTED_MESSAGE;
		assertEquals(List.of(message, message, message),
				List.of(uploaded.getErrorMessage(), characters.getErrorMessage(), scenes.getErrorMessage()));
	}
}
