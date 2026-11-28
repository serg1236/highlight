package com.highlight.service.support;

import com.highlight.ai.MergeCluster;
import com.highlight.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CharacterMergerTest {

	private static final Long BOOK_ID = 7L;

	private final CharacterMerger merger = new CharacterMerger();

	@Test
	void assemble_clusteredCandidates_combinedWithSortedObservations() {
		// given
		var hair = observation(ObservationCategory.HAIR, "red hair", 40, 11L);
		var coat = observation(ObservationCategory.OUTERWEAR, "green coat", 10, 10L);
		var hat = observation(ObservationCategory.HEADWEAR, "blue hat", null, 12L);
		var candidates = List.of(
				new CandidateCharacter(0, "Anna", List.of("Ann", "ANNA"), CharacterSex.UNDEFINED, List.of(hair, hat)),
				new CandidateCharacter(1, "Ann", List.of("Annie", " "), CharacterSex.UNDEFINED, List.of(coat)));
		var clusters = List.of(new MergeCluster("Anna Smith", List.of(0, 1)));
		var expected = List.of(new Snapshot(BOOK_ID, "Anna Smith", CharacterSex.UNDEFINED, 10, List.of("Anna", "Ann", "Annie"),
				List.of(coat, hair, hat)));

		// when
		var actual = merger.assemble(BOOK_ID, candidates, clusters);

		// then
		assertEquals(expected, snapshots(actual));
	}

	@Test
	void assemble_roleObservation_keepsInferredFlag() {
		// given
		var occupation = new LocatedObservation(ObservationKind.ROLE, ObservationCategory.OCCUPATION, "housekeeper",
				"Mrs. Hill brought the tea", 20, 45, 10L, false, true);
		var candidates = List.of(new CandidateCharacter(0, "Mrs. Hill", List.of(), CharacterSex.FEMALE,
				List.of(occupation)));
		var expected = List.of(new Snapshot(BOOK_ID, "Mrs. Hill", CharacterSex.FEMALE, 20, List.of(),
				List.of(occupation)));

		// when
		var actual = merger.assemble(BOOK_ID, candidates, List.of());

		// then
		assertEquals(expected, snapshots(actual));
	}

	@Test
	void assemble_defensiveAgainstModelMistakes() {
		// given: unknown id, null id, id reused, blank name, omitted candidate, duplicate canonical name
		var candidates = List.of(
				new CandidateCharacter(0, "Darcy", List.of(), CharacterSex.UNDEFINED, List.of()),
				new CandidateCharacter(1, "Mr. Darcy", List.of(), CharacterSex.UNDEFINED, List.of()),
				new CandidateCharacter(2, "Jane", List.of(), CharacterSex.UNDEFINED, List.of()),
				new CandidateCharacter(3, "Bingley", List.of(), CharacterSex.UNDEFINED, List.of()),
				new CandidateCharacter(4, "Fitzwilliam Darcy", List.of(), CharacterSex.UNDEFINED, List.of()));
		var clusters = List.of(
				new MergeCluster("Fitzwilliam Darcy", Arrays.asList(0, 99, null)),
				new MergeCluster(" ", List.of(2, 0)),
				new MergeCluster("fitzwilliam darcy", List.of(1)),
				new MergeCluster("Nobody", List.of(42)));
		var expected = List.of(
				new Snapshot(BOOK_ID, "Fitzwilliam Darcy", CharacterSex.UNDEFINED, null, List.of("Darcy", "Mr. Darcy"), List.of()),
				new Snapshot(BOOK_ID, "Jane", CharacterSex.UNDEFINED, null, List.of(), List.of()),
				new Snapshot(BOOK_ID, "Bingley", CharacterSex.UNDEFINED, null, List.of(), List.of()));

		// when
		var actual = merger.assemble(BOOK_ID, candidates, clusters);

		// then
		assertEquals(expected, snapshots(actual));
	}

	@Test
	void assemble_noClusters_eachCandidateSeparate() {
		// given
		var candidates = List.of(
				new CandidateCharacter(0, "Anna", List.of(), CharacterSex.UNDEFINED, List.of()),
				new CandidateCharacter(1, "Boris", List.of("Bo"), CharacterSex.UNDEFINED, List.of()));
		var expected = List.of(
				new Snapshot(BOOK_ID, "Anna", CharacterSex.UNDEFINED, null, List.of(), List.of()),
				new Snapshot(BOOK_ID, "Boris", CharacterSex.UNDEFINED, null, List.of("Bo"), List.of()));

		// when
		var actual = merger.assemble(BOOK_ID, candidates, List.of());

		// then
		assertEquals(expected, snapshots(actual));
	}

	@ParameterizedTest
	@MethodSource("memberSexes")
	void assemble_clusteredCandidates_sexAgreedByMembers(CharacterSex first, CharacterSex second,
														 CharacterSex expectedSex) {
		// given
		var candidates = List.of(
				new CandidateCharacter(0, "Anna", List.of(), first, List.of()),
				new CandidateCharacter(1, "Ann", List.of(), second, List.of()));
		var clusters = List.of(new MergeCluster("Anna", List.of(0, 1)));
		var expected = List.of(new Snapshot(BOOK_ID, "Anna", expectedSex, null, List.of("Ann"), List.of()));

		// when
		var actual = merger.assemble(BOOK_ID, candidates, clusters);

		// then
		assertEquals(expected, snapshots(actual));
	}

	static Stream<Arguments> memberSexes() {
		return Stream.of(
				Arguments.of(CharacterSex.FEMALE, CharacterSex.FEMALE, CharacterSex.FEMALE),
				Arguments.of(CharacterSex.UNDEFINED, CharacterSex.MALE, CharacterSex.MALE),
				Arguments.of(CharacterSex.MALE, CharacterSex.FEMALE, CharacterSex.UNDEFINED),
				Arguments.of(CharacterSex.UNDEFINED, CharacterSex.UNDEFINED, CharacterSex.UNDEFINED));
	}

	private static LocatedObservation observation(ObservationCategory category, String description, Integer start,
												  Long chapterId) {
		return new LocatedObservation(category == ObservationCategory.HAIR ? ObservationKind.APPEARANCE
				: ObservationKind.CLOTHING, category, description, "quote " + description, start,
				start == null ? null : start + 5, chapterId, false, false);
	}

	static List<Snapshot> snapshots(List<BookCharacter> characters) {
		return characters.stream().map(character -> new Snapshot(character.getBookId(), character.getCanonicalName(),
						character.getSex(), character.getFirstOffset(), character.getAliases().stream().map(CharacterAlias::getAlias).toList(),
						character.getObservations().stream()
								.map(observation -> new LocatedObservation(observation.getKind(), observation.getCategory(),
										observation.getDescription(), observation.getQuote(), observation.getStartOffset(),
										observation.getEndOffset(), observation.getChapterId(), observation.isExplicitChange(),
										observation.isInferred()))
								.toList()))
				.toList();
	}

	record Snapshot(Long bookId, String canonicalName, CharacterSex sex, Integer firstOffset, List<String> aliases,
					List<LocatedObservation> observations) {
	}
}
