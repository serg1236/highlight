package com.highlight.service.impl;

import com.highlight.dto.CharacterDto;
import com.highlight.dto.CharacterStateDto;
import com.highlight.dto.ObservationDto;
import com.highlight.entity.*;
import com.highlight.exception.ResourceNotFoundException;
import com.highlight.repository.BookCharacterRepository;
import com.highlight.repository.BookRepository;
import com.highlight.service.CharacterQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterQueryServiceImpl implements CharacterQueryService {

	private final BookRepository bookRepository;

	private final BookCharacterRepository characterRepository;

	@Override
	public List<CharacterDto> getCharacters(Long bookId) {
		if (!bookRepository.existsById(bookId)) {
			throw new ResourceNotFoundException("Book " + bookId + " not found");
		}
		return characterRepository.findAllByBookIdOrderByFirstOffsetAscIdAsc(bookId).stream()
				.map(CharacterQueryServiceImpl::toDto)
				.toList();
	}

	/**
	 * For each kind and category, the observation with the greatest start offset not after {@code offset} wins.
	 * Observations whose quote could not be located have no offset and are ignored.
	 */
	@Override
	public CharacterStateDto getStateAt(Long characterId, int offset) {
		var character = characterRepository.findById(characterId)
				.orElseThrow(() -> new ResourceNotFoundException("Character " + characterId + " not found"));
		Map<ObservationCategory, CharacterObservation> latest = character.getObservations().stream()
				.filter(observation -> observation.getStartOffset() != null && observation.getStartOffset() <= offset)
				.collect(Collectors.toMap(CharacterObservation::getCategory, observation -> observation, later()));
		return CharacterStateDto.builder()
				.characterId(character.getId())
				.canonicalName(character.getCanonicalName())
				.sex(character.getSex())
				.offset(offset)
				.appearance(ofKind(latest, ObservationKind.APPEARANCE))
				.clothing(ofKind(latest, ObservationKind.CLOTHING))
				.role(ofKind(latest, ObservationKind.ROLE))
				.build();
	}

	private static BinaryOperator<CharacterObservation> later() {
		return (first, second) -> second.getStartOffset() >= first.getStartOffset() ? second : first;
	}

	private static List<ObservationDto> ofKind(Map<ObservationCategory, CharacterObservation> latest,
											   ObservationKind kind) {
		return latest.values().stream()
				.filter(observation -> observation.getKind() == kind)
				.sorted(Comparator.comparing(CharacterObservation::getCategory))
				.map(CharacterQueryServiceImpl::toDto)
				.toList();
	}

	private static CharacterDto toDto(BookCharacter character) {
		return CharacterDto.builder()
				.id(character.getId())
				.canonicalName(character.getCanonicalName())
				.sex(character.getSex())
				.firstOffset(character.getFirstOffset())
				.aliases(character.getAliases().stream().map(CharacterAlias::getAlias).toList())
				.observations(character.getObservations().stream()
						.sorted(Comparator.comparing(CharacterObservation::getStartOffset,
								Comparator.nullsLast(Comparator.naturalOrder())))
						.map(CharacterQueryServiceImpl::toDto)
						.toList())
				.build();
	}

	private static ObservationDto toDto(CharacterObservation observation) {
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
