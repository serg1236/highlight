package com.highlight.controller;

import com.highlight.dto.*;
import com.highlight.service.BookService;
import com.highlight.service.CharacterQueryService;
import com.highlight.service.StoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

	private final BookService bookService;

	private final CharacterQueryService characterQueryService;

	private final StoryQueryService storyQueryService;

	/**
	 * Accepts an EPUB, FB2 or TXT file; extraction continues in the background (poll {@code GET /api/books/{id}}).
	 */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<BookDto> upload(@RequestParam("file") MultipartFile file) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(bookService.upload(file));
	}

	@GetMapping
	public List<BookDto> listBooks() {
		return bookService.listBooks();
	}

	@GetMapping("/{bookId}")
	public BookDto getBook(@PathVariable Long bookId) {
		return bookService.getBook(bookId);
	}

	@DeleteMapping("/{bookId}")
	public ResponseEntity<Void> deleteBook(@PathVariable Long bookId) {
		bookService.deleteBook(bookId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{bookId}/characters")
	public List<CharacterDto> getCharacters(@PathVariable Long bookId) {
		return characterQueryService.getCharacters(bookId);
	}

	@GetMapping("/{bookId}/scenes")
	public List<SceneDto> getScenes(@PathVariable Long bookId,
									@RequestParam(defaultValue = "false") boolean dialogOnly) {
		return storyQueryService.getScenes(bookId, dialogOnly);
	}

	@GetMapping("/{bookId}/locations")
	public List<LocationDto> getLocations(@PathVariable Long bookId) {
		return storyQueryService.getLocations(bookId);
	}

	@GetMapping("/{bookId}/setting")
	public SettingDto getSetting(@PathVariable Long bookId) {
		return storyQueryService.getSetting(bookId);
	}
}
