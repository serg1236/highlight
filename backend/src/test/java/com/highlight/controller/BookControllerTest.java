package com.highlight.controller;

import com.highlight.dto.*;
import com.highlight.entity.*;
import com.highlight.exception.ResourceNotFoundException;
import com.highlight.exception.UnsupportedBookFormatException;
import com.highlight.service.BookService;
import com.highlight.service.CharacterQueryService;
import com.highlight.service.StoryQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {BookController.class, GlobalExceptionHandler.class, BookControllerTest.TestConfig.class})
@AutoConfigureMockMvc
class BookControllerTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BookService bookService;

	@MockitoBean
	private CharacterQueryService characterQueryService;

	@MockitoBean
	private StoryQueryService storyQueryService;

	@Test
	void upload_accepted() throws Exception {
		// given
		var file = new MockMultipartFile("file", "tale.txt", "text/plain", "Text.".getBytes());
		var dto = book(BookStatus.UPLOADED);
		when(bookService.upload(file)).thenReturn(dto);

		// when / then
		mockMvc.perform(multipart("/api/books").file(file))
				.andExpect(status().isAccepted())
				.andExpect(content().json(JSON.writeValueAsString(dto), JsonCompareMode.STRICT));
	}

	@Test
	void upload_unsupportedFormat_badRequest() throws Exception {
		// given
		var file = new MockMultipartFile("file", "tale.pdf", "application/pdf", "%PDF".getBytes());
		when(bookService.upload(file)).thenThrow(new UnsupportedBookFormatException("Unsupported file 'tale.pdf'"));

		// when / then
		mockMvc.perform(multipart("/api/books").file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Unsupported file 'tale.pdf'"));
	}

	@Test
	void upload_missingFilePart_badRequest() throws Exception {
		mockMvc.perform(multipart("/api/books"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listBooks_ok() throws Exception {
		// given
		var dtos = List.of(book(BookStatus.READY));
		when(bookService.listBooks()).thenReturn(dtos);

		// when / then
		mockMvc.perform(get("/api/books"))
				.andExpect(status().isOk())
				.andExpect(content().json(JSON.writeValueAsString(dtos), JsonCompareMode.STRICT));
	}

	@Test
	void getBook_ok() throws Exception {
		// given
		var dto = book(BookStatus.EXTRACTING_SCENES);
		when(bookService.getBook(1L)).thenReturn(dto);

		// when / then
		mockMvc.perform(get("/api/books/1"))
				.andExpect(status().isOk())
				.andExpect(content().json(JSON.writeValueAsString(dto), JsonCompareMode.STRICT));
	}

	@Test
	void getBook_notFound() throws Exception {
		// given
		when(bookService.getBook(9L)).thenThrow(new ResourceNotFoundException("Book 9 not found"));

		// when / then
		mockMvc.perform(get("/api/books/9"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Book 9 not found"));
	}

	@Test
	void getBook_invalidId_badRequest() throws Exception {
		mockMvc.perform(get("/api/books/abc"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void deleteBook_noContent() throws Exception {
		mockMvc.perform(delete("/api/books/1"))
				.andExpect(status().isNoContent());

		verify(bookService).deleteBook(1L);
	}

	@Test
	void deleteBook_notFound() throws Exception {
		// given
		doThrow(new ResourceNotFoundException("Book 9 not found")).when(bookService).deleteBook(9L);

		// when / then
		mockMvc.perform(delete("/api/books/9"))
				.andExpect(status().isNotFound());
	}

	@Test
	void getCharacters_ok() throws Exception {
		// given
		var dtos = List.of(CharacterDto.builder()
				.id(5L)
				.canonicalName("Anna Smith")
				.sex(CharacterSex.FEMALE)
				.firstOffset(10)
				.aliases(List.of("Ann"))
				.observations(List.of(ObservationDto.builder().id(1L).kind(ObservationKind.APPEARANCE)
						.category(ObservationCategory.HAIR).description("red hair").quote("Anna had red hair")
						.startOffset(10).endOffset(27).chapterId(3L).build()))
				.build());
		when(characterQueryService.getCharacters(1L)).thenReturn(dtos);

		// when / then
		mockMvc.perform(get("/api/books/1/characters"))
				.andExpect(status().isOk())
				.andExpect(content().json(JSON.writeValueAsString(dtos), JsonCompareMode.STRICT));
	}

	@Test
	void getCharacters_bookNotFound() throws Exception {
		// given
		when(characterQueryService.getCharacters(9L)).thenThrow(new ResourceNotFoundException("Book 9 not found"));

		// when / then
		mockMvc.perform(get("/api/books/9/characters"))
				.andExpect(status().isNotFound());
	}

	@Test
	void getScenes_defaultAllScenes_ok() throws Exception {
		// given
		var dtos = List.of(SceneDto.builder()
				.id(20L)
				.sceneIndex(0)
				.chapterId(3L)
				.startOffset(10)
				.endOffset(90)
				.summary("Anna argues.")
				.hasDialog(true)
				.location(SceneLocationDto.builder().id(7L).name("The inn").build())
				.timeOfDay(TimeOfDay.NIGHT)
				.characters(List.of(SceneCharacterDto.builder().characterId(5L).name("Anna Smith").speaks(true).build()))
				.build());
		when(storyQueryService.getScenes(1L, false)).thenReturn(dtos);

		// when / then
		mockMvc.perform(get("/api/books/1/scenes"))
				.andExpect(status().isOk())
				.andExpect(content().json(JSON.writeValueAsString(dtos), JsonCompareMode.STRICT));
	}

	@Test
	void getScenes_dialogOnly_ok() throws Exception {
		// given
		when(storyQueryService.getScenes(1L, true)).thenReturn(List.of());

		// when / then
		mockMvc.perform(get("/api/books/1/scenes").param("dialogOnly", "true"))
				.andExpect(status().isOk())
				.andExpect(content().json("[]", JsonCompareMode.STRICT));
	}

	@Test
	void getScenes_invalidDialogOnly_badRequest() throws Exception {
		mockMvc.perform(get("/api/books/1/scenes").param("dialogOnly", "maybe"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getScenes_bookNotFound() throws Exception {
		// given
		when(storyQueryService.getScenes(9L, false)).thenThrow(new ResourceNotFoundException("Book 9 not found"));

		// when / then
		mockMvc.perform(get("/api/books/9/scenes"))
				.andExpect(status().isNotFound());
	}

	@Test
	void getLocations_ok() throws Exception {
		// given
		var dtos = List.of(LocationDto.builder().id(7L).name("The inn").visualDescription("smoky taproom")
				.firstOffset(10).build());
		when(storyQueryService.getLocations(1L)).thenReturn(dtos);

		// when / then
		mockMvc.perform(get("/api/books/1/locations"))
				.andExpect(status().isOk())
				.andExpect(content().json(JSON.writeValueAsString(dtos), JsonCompareMode.STRICT));
	}

	@Test
	void getLocations_bookNotFound() throws Exception {
		// given
		when(storyQueryService.getLocations(9L)).thenThrow(new ResourceNotFoundException("Book 9 not found"));

		// when / then
		mockMvc.perform(get("/api/books/9/locations"))
				.andExpect(status().isNotFound());
	}

	@Test
	void getSetting_ok() throws Exception {
		// given
		var dto = SettingDto.builder()
				.bookId(1L)
				.realism(Realism.REALISTIC)
				.period("c. 1880")
				.periodSource(EvidenceSource.INFERRED)
				.promptLine("Victorian London")
				.clues(List.of(SettingClueDto.builder().id(9L).category(SettingClueCategory.TECHNOLOGY)
						.description("gas lamps").quote("the gas lamps hissed").startOffset(40).endOffset(60)
						.chapterId(3L).build()))
				.build();
		when(storyQueryService.getSetting(1L)).thenReturn(dto);

		// when / then
		mockMvc.perform(get("/api/books/1/setting"))
				.andExpect(status().isOk())
				.andExpect(content().json(JSON.writeValueAsString(dto), JsonCompareMode.STRICT));
	}

	@Test
	void getSetting_notFound() throws Exception {
		// given
		when(storyQueryService.getSetting(1L)).thenThrow(new ResourceNotFoundException("Setting of book 1 not found"));

		// when / then
		mockMvc.perform(get("/api/books/1/setting"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Setting of book 1 not found"));
	}

	private static BookDto book(BookStatus status) {
		return BookDto.builder()
				.id(1L)
				.title("Tale")
				.originalFilename("tale.txt")
				.format(BookFormat.TXT)
				.status(status)
				.charCount(5)
				.chapterCount(1)
				.chunksTotal(1)
				.costUsd(new BigDecimal("0.001200"))
				.build();
	}

	@Configuration
	@EnableWebMvc
	static class TestConfig {
	}
}
