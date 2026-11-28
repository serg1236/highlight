package com.highlight.service.impl;

import com.highlight.dto.*;
import com.highlight.entity.*;
import com.highlight.exception.ResourceNotFoundException;
import com.highlight.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryQueryServiceImplTest {

	private static final Long BOOK_ID = 1L;

	@Mock
	private BookRepository bookRepository;

	@Mock
	private BookSceneRepository sceneRepository;

	@Mock
	private BookLocationRepository locationRepository;

	@Mock
	private BookSettingRepository settingRepository;

	@Mock
	private SettingClueRepository clueRepository;

	@InjectMocks
	private StoryQueryServiceImpl service;

	private BookLocation inn;

	private BookScene dialogScene;

	private BookScene quietScene;

	@BeforeEach
	void setUp() {
		inn = BookLocation.builder().id(7L).bookId(BOOK_ID).name("The inn").visualDescription("smoky taproom")
				.firstOffset(10).build();
		var anna = BookCharacter.builder().id(5L).bookId(BOOK_ID).canonicalName("Anna Smith").build();
		dialogScene = BookScene.builder().id(20L).bookId(BOOK_ID).sceneIndex(0).chapterId(3L).startOffset(10)
				.endOffset(90).summary("Anna argues.").hasDialog(true).location(inn).locationQuote("the smoky inn")
				.timeOfDay(TimeOfDay.NIGHT).timeMarker("that night").timelineShift(true).timelineNote("a memory")
				.build();
		dialogScene.addCharacter(SceneCharacter.builder().id(30L).character(anna).speaks(true).build());
		quietScene = BookScene.builder().id(21L).bookId(BOOK_ID).sceneIndex(1).summary("Rain falls.").build();
	}

	@Test
	void getScenes_all_mapsScenesInOrder() {
		// given
		when(bookRepository.existsById(BOOK_ID)).thenReturn(true);
		when(sceneRepository.findAllByBookIdOrderBySceneIndex(BOOK_ID)).thenReturn(List.of(dialogScene, quietScene));
		var expected = List.of(
				SceneDto.builder().id(20L).sceneIndex(0).chapterId(3L).startOffset(10).endOffset(90)
						.summary("Anna argues.").hasDialog(true)
						.location(SceneLocationDto.builder().id(7L).name("The inn").build())
						.locationQuote("the smoky inn").timeOfDay(TimeOfDay.NIGHT).timeMarker("that night")
						.timelineShift(true).timelineNote("a memory")
						.characters(List.of(SceneCharacterDto.builder().characterId(5L).name("Anna Smith").speaks(true)
								.build()))
						.build(),
				SceneDto.builder().id(21L).sceneIndex(1).summary("Rain falls.").timeOfDay(TimeOfDay.UNKNOWN)
						.characters(List.of()).build());

		// when
		var actual = service.getScenes(BOOK_ID, false);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void getScenes_dialogOnly_usesDialogQuery() {
		// given
		when(bookRepository.existsById(BOOK_ID)).thenReturn(true);
		when(sceneRepository.findAllByBookIdAndHasDialogTrueOrderBySceneIndex(BOOK_ID)).thenReturn(List.of());

		// when
		var actual = service.getScenes(BOOK_ID, true);

		// then
		assertEquals(List.of(), actual);
	}

	@Test
	void getLocations_mapsLocations() {
		// given
		when(bookRepository.existsById(BOOK_ID)).thenReturn(true);
		when(locationRepository.findAllByBookIdOrderByFirstOffsetAscIdAsc(BOOK_ID)).thenReturn(List.of(inn));
		var expected = List.of(LocationDto.builder().id(7L).name("The inn").visualDescription("smoky taproom")
				.firstOffset(10).build());

		// when
		var actual = service.getLocations(BOOK_ID);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void getSetting_mapsSettingWithClues() {
		// given
		when(bookRepository.existsById(BOOK_ID)).thenReturn(true);
		when(settingRepository.findById(BOOK_ID)).thenReturn(Optional.of(BookSetting.builder().bookId(BOOK_ID)
				.realism(Realism.REALISTIC).genre("drama").period("c. 1880").periodSource(EvidenceSource.INFERRED)
				.region("London").technology("gas lamps").clothingNorms("frock coats").architecture("terraces")
				.visualMood("foggy").promptLine("Victorian London").build()));
		when(clueRepository.findAllByBookIdOrderByStartOffsetAscIdAsc(BOOK_ID)).thenReturn(List.of(SettingClue
				.builder().id(9L).bookId(BOOK_ID).category(SettingClueCategory.TECHNOLOGY).description("gas lamps")
				.quote("the gas lamps hissed").startOffset(40).endOffset(60).chapterId(3L).build()));
		var expected = SettingDto.builder().bookId(BOOK_ID).realism(Realism.REALISTIC).genre("drama")
				.period("c. 1880").periodSource(EvidenceSource.INFERRED).region("London").technology("gas lamps")
				.clothingNorms("frock coats").architecture("terraces").visualMood("foggy")
				.promptLine("Victorian London")
				.clues(List.of(SettingClueDto.builder().id(9L).category(SettingClueCategory.TECHNOLOGY)
						.description("gas lamps").quote("the gas lamps hissed").startOffset(40).endOffset(60)
						.chapterId(3L).build()))
				.build();

		// when
		var actual = service.getSetting(BOOK_ID);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void getSetting_notSynthesizedYet_shouldThrowException() {
		when(bookRepository.existsById(BOOK_ID)).thenReturn(true);
		when(settingRepository.findById(BOOK_ID)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class, () -> service.getSetting(BOOK_ID));
	}

	@ParameterizedTest
	@ValueSource(strings = {"scenes", "locations", "setting"})
	void query_bookNotFound_shouldThrowException(String query) {
		when(bookRepository.existsById(BOOK_ID)).thenReturn(false);

		assertThrows(ResourceNotFoundException.class, () -> {
			switch (query) {
				case "scenes" -> service.getScenes(BOOK_ID, false);
				case "locations" -> service.getLocations(BOOK_ID);
				default -> service.getSetting(BOOK_ID);
			}
		});
	}
}
