package com.highlight.service;

/**
 * Background job that extracts characters, scenes, locations and the world setting of one book.
 */
public interface BookExtractionService {

	/**
	 * Runs the whole extraction, replacing earlier results. Never throws: failures set the book to FAILED.
	 */
	void extract(Long bookId);
}
