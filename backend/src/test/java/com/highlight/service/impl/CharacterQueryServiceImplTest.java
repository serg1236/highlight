package com.highlight.service.impl;

import com.highlight.dto.CharacterDto;
import com.highlight.dto.CharacterStateDto;
import com.highlight.dto.ObservationDto;
import com.highlight.entity.*;
import com.highlight.exception.ResourceNotFoundException;
import com.highlight.repository.BookCharacterRepository;
import com.highlight.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterQueryServiceImplTest {

	private static final Long BOOK_ID = 1L;

	private static final Long CHARACTER_ID = 5L;

	@Mock
	private BookRepository bookRepository;

	@Mock
	private BookCharacterRepository characterRepository;

	@InjectMocks
	private CharacterQueryServiceImpl service;

	private BookCharacter character;

	private CharacterObservation redHair;

	private CharacterObservation coat;

	private CharacterObservation shortHair;

	private CharacterObservation unlocated;

	@BeforeEach
	void setUp() {
		character = BookCharacter.builder().id(CHARACTER_ID).bookId(BOOK_ID).canonicalName("Anna Smith")
				.sex(CharacterSex.FEMALE).firstOffset(10).build();
		character.addAlias(CharacterAlias.builder().id(1L).alias("Ann").build());
		unlocated = observation(4L, ObservationKind.CLOTHING, ObservationCategory.HEADWEAR, "blue hat", null, false);
		shortHair = observation(3L, ObservationKind.APPEARANCE, ObservationCategory.HAIR, "short red hair", 50, true);
		redHair = observation(1L, ObservationKind.APPEARANCE, ObservationCategory.HAIR, "long red hair", 10, false);
		coat = observation(2L, ObservationKind.CLOTHING, ObservationCategory.OUTERWEAR, "green coat", 30, false);
		List.of(unlocated, shortHair, redHair, coat).forEach(character::addObservation);
	}

	@Test
	void getCharacters_mapsAliasesAndObservationsInTextOrder() {
		// given
		when(bookRepository.existsById(BOOK_ID)).thenReturn(true);
		when(characterRepository.findAllByBookIdOrderByFirstOffsetAscIdAsc(BOOK_ID)).thenReturn(List.of(character));
		var expected = List.of(CharacterDto.builder()
				.id(CHARACTER_ID)
				.canonicalName("Anna Smith")
				.sex(CharacterSex.FEMALE)
				.firstOffset(10)
				.aliases(List.of("Ann"))
				.observations(List.of(dto(redHair), dto(coat), dto(shortHair), dto(unlocated)))
				.build());

		// when
		var actual = service.getCharacters(BOOK_ID);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void getCharacters_bookNotFound_shouldThrowException() {
		when(bookRepository.existsById(BOOK_ID)).thenReturn(false);

		assertThrows(ResourceNotFoundException.class, () -> service.getCharacters(BOOK_ID));
	}

	@ParameterizedTest(name = "offset {0}")
	@CsvSource({
			"5, , ",
			"10, 1, ",
			"40, 1, 2",
			"50, 3, 2",
			"1000, 3, 2"
	})
	void getStateAt_latestObservationPerCategory(int offset, Long hairId, Long coatId) {
		// given
		when(characterRepository.findById(CHARACTER_ID)).thenReturn(Optional.of(character));
		var appearance = hairId == null ? List.<ObservationDto>of() : List.of(dto(byId(hairId)));
		var clothing = coatId == null ? List.<ObservationDto>of() : List.of(dto(byId(coatId)));
		var expected = CharacterStateDto.builder()
				.characterId(CHARACTER_ID)
				.canonicalName("Anna Smith")
				.sex(CharacterSex.FEMALE)
				.offset(offset)
				.appearance(appearance)
				.clothing(clothing)
				.role(List.of())
				.build();

		// when
		var actual = service.getStateAt(CHARACTER_ID, offset);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void getStateAt_sameOffsetTwice_laterEntryWins() {
		// given
		var sameOffset = observation(6L, ObservationKind.APPEARANCE, ObservationCategory.HAIR, "grey hair", 10, false);
		character.addObservation(sameOffset);
		when(characterRepository.findById(CHARACTER_ID)).thenReturn(Optional.of(character));
		var expected = CharacterStateDto.builder()
				.characterId(CHARACTER_ID)
				.canonicalName("Anna Smith")
				.sex(CharacterSex.FEMALE)
				.offset(20)
				.appearance(List.of(dto(sameOffset)))
				.clothing(List.of())
				.role(List.of())
				.build();

		// when
		var actual = service.getStateAt(CHARACTER_ID, 20);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void getStateAt_roleObservations_returnedAsRole() {
		// given
		var occupation = CharacterObservation.builder().id(7L).kind(ObservationKind.ROLE)
				.category(ObservationCategory.OCCUPATION).description("governess").quote("the new governess")
				.startOffset(15).endOffset(32).chapterId(100L).inferred(true).build();
		character.addObservation(occupation);
		when(characterRepository.findById(CHARACTER_ID)).thenReturn(Optional.of(character));
		var expected = CharacterStateDto.builder()
				.characterId(CHARACTER_ID)
				.canonicalName("Anna Smith")
				.sex(CharacterSex.FEMALE)
				.offset(20)
				.appearance(List.of(dto(redHair)))
				.clothing(List.of())
				.role(List.of(dto(occupation)))
				.build();

		// when
		var actual = service.getStateAt(CHARACTER_ID, 20);

		// then
		assertEquals(expected, actual);
	}

	@Test
	void getStateAt_characterNotFound_shouldThrowException() {
		when(characterRepository.findById(CHARACTER_ID)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class, () -> service.getStateAt(CHARACTER_ID, 0));
	}

	private CharacterObservation byId(Long id) {
		return character.getObservations().stream().filter(o -> o.getId().equals(id)).findFirst().orElseThrow();
	}

	private static CharacterObservation observation(Long id, ObservationKind kind, ObservationCategory category,
													String description, Integer start, boolean explicitChange) {
		return CharacterObservation.builder().id(id).kind(kind).category(category).description(description)
				.quote("quote " + description).startOffset(start).endOffset(start == null ? null : start + 5)
				.chapterId(100L).explicitChange(explicitChange).build();
	}

	private static ObservationDto dto(CharacterObservation observation) {
		return ObservationDto.builder()
				.id(observation.getId())
				.kind(observation.getKind())
				.category(observation.getCategory())
				.description(observation.getDescription())
				.quote(observation.getQuote())
				.startOffset(observation.getStartOffset())
				.endOffset(observation.getEndOffset())
				.chapterId(observation.getChapterId())
				.explicitChange(observation.isExplicitChange())
				.inferred(observation.isInferred())
				.build();
	}
}
