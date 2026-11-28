package com.highlight.controller;

import com.highlight.dto.CharacterStateDto;
import com.highlight.dto.ObservationDto;
import com.highlight.entity.CharacterSex;
import com.highlight.entity.ObservationCategory;
import com.highlight.entity.ObservationKind;
import com.highlight.exception.ResourceNotFoundException;
import com.highlight.service.CharacterQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {CharacterController.class, GlobalExceptionHandler.class,
		CharacterControllerTest.TestConfig.class})
@AutoConfigureMockMvc
class CharacterControllerTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CharacterQueryService characterQueryService;

	@Test
	void getState_ok() throws Exception {
		// given
		var dto = CharacterStateDto.builder()
				.characterId(5L)
				.canonicalName("Anna Smith")
				.sex(CharacterSex.FEMALE)
				.offset(120)
				.appearance(List.of(ObservationDto.builder().id(1L).kind(ObservationKind.APPEARANCE)
						.category(ObservationCategory.HAIR).description("short red hair").quote("she had cut her hair")
						.startOffset(100).endOffset(120).chapterId(4L).explicitChange(true).build()))
				.clothing(List.of())
				.build();
		when(characterQueryService.getStateAt(5L, 120)).thenReturn(dto);

		// when / then
		mockMvc.perform(get("/api/characters/5/state").param("offset", "120"))
				.andExpect(status().isOk())
				.andExpect(content().json(JSON.writeValueAsString(dto), JsonCompareMode.STRICT));
	}

	@Test
	void getState_negativeOffset_badRequest() throws Exception {
		mockMvc.perform(get("/api/characters/5/state").param("offset", "-1"))
				.andExpect(status().isBadRequest());

		verify(characterQueryService, never()).getStateAt(anyLong(), anyInt());
	}

	@Test
	void getState_missingOffset_badRequest() throws Exception {
		mockMvc.perform(get("/api/characters/5/state"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getState_characterNotFound() throws Exception {
		// given
		when(characterQueryService.getStateAt(9L, 0)).thenThrow(new ResourceNotFoundException("Character 9 not found"));

		// when / then
		mockMvc.perform(get("/api/characters/9/state").param("offset", "0"))
				.andExpect(status().isNotFound());
	}

	@Configuration
	@EnableWebMvc
	static class TestConfig {
	}
}
