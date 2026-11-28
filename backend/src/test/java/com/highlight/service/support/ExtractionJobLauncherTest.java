package com.highlight.service.support;

import com.highlight.event.BookUploadedEvent;
import com.highlight.service.BookExtractionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExtractionJobLauncherTest {

	@Mock
	private BookExtractionService extractionService;

	@InjectMocks
	private ExtractionJobLauncher launcher;

	@Test
	void onBookUploaded_runsExtraction() {
		launcher.onBookUploaded(new BookUploadedEvent(3L));

		verify(extractionService).extract(3L);
	}
}
