package com.highlight.ai;

/**
 * The model call finished but returned no usable result (refusal, truncated output, empty content).
 */
public class ExtractionException extends RuntimeException {

	public ExtractionException(String message) {
		super(message);
	}
}
