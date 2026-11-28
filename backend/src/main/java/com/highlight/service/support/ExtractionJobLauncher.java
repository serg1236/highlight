package com.highlight.service.support;

import com.highlight.config.AppConfig;
import com.highlight.event.BookUploadedEvent;
import com.highlight.service.BookExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts extraction on the extraction executor once the upload transaction has committed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractionJobLauncher {

	private final BookExtractionService extractionService;

	@Async(AppConfig.EXTRACTION_EXECUTOR)
	@TransactionalEventListener
	public void onBookUploaded(BookUploadedEvent event) {
		log.info("Extraction job started: bookId={}", event.bookId());
		extractionService.extract(event.bookId());
	}
}
