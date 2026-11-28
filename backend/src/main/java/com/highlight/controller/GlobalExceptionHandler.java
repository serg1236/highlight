package com.highlight.controller;

import com.highlight.exception.BookParseException;
import com.highlight.exception.ResourceNotFoundException;
import com.highlight.exception.UnsupportedBookFormatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler({BookParseException.class, UnsupportedBookFormatException.class})
	public ProblemDetail handleBadBook(RuntimeException e) {
		log.warn("Rejected book upload: {}", e.getMessage());
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ProblemDetail handleNotFound(ResourceNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ProblemDetail handleTooLarge(MaxUploadSizeExceededException e) {
		log.warn("Rejected book upload: {}", e.getMessage());
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "The uploaded file is too large");
	}
}
