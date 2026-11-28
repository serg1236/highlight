package com.highlight.service.support;

import com.highlight.entity.BookStatus;
import com.highlight.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Jobs run in memory, so any book still waiting or extracting at startup was interrupted by a restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterruptedExtractionRecovery {

	static final String INTERRUPTED_MESSAGE = "Extraction was interrupted by an application restart";

	private final BookRepository bookRepository;

	@EventListener(ApplicationReadyEvent.class)
	public void markInterruptedBooksFailed() {
		var books = bookRepository.findAllByStatusIn(List.of(BookStatus.UPLOADED,
				BookStatus.EXTRACTING_CHARACTERS, BookStatus.EXTRACTING_SCENES));
		for (var book : books) {
			book.setStatus(BookStatus.FAILED);
			book.setErrorMessage(INTERRUPTED_MESSAGE);
			log.warn("Marked interrupted extraction as failed: bookId={}", book.getId());
		}
		bookRepository.saveAll(books);
	}
}
