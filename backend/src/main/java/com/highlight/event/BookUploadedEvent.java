package com.highlight.event;

/**
 * Published inside the upload transaction; extraction starts after it commits.
 */
public record BookUploadedEvent(Long bookId) {
}
