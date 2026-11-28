package com.highlight.exception;

/**
 * The uploaded file could not be turned into book text.
 */
public class BookParseException extends RuntimeException {

	public BookParseException(String message) {
		super(message);
	}

	public BookParseException(String message, Throwable cause) {
		super(message, cause);
	}
}
