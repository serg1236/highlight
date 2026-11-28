package com.highlight.service;

import com.highlight.dto.BookDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BookService {

	/**
	 * Parses and stores the book, then starts extraction (characters, scenes, setting) in the background.
	 */
	BookDto upload(MultipartFile file);

	List<BookDto> listBooks();

	BookDto getBook(Long bookId);

	void deleteBook(Long bookId);
}
