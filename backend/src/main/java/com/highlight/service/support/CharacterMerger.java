package com.highlight.service.support;

import com.highlight.ai.MergeCluster;
import com.highlight.entity.BookCharacter;
import com.highlight.entity.CharacterAlias;
import com.highlight.entity.CharacterObservation;
import com.highlight.entity.CharacterSex;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns per-chunk candidates and the model's identity clusters into one character per person.
 * The model's answer is treated defensively: unknown ids are ignored, an id is used once, candidates the model
 * left out become their own character, and characters with the same name are combined.
 */
@Component
public class CharacterMerger {

	private static final Comparator<CharacterObservation> BY_OFFSET = Comparator.comparing(
			CharacterObservation::getStartOffset, Comparator.nullsLast(Comparator.naturalOrder()));

	public List<BookCharacter> assemble(Long bookId, List<CandidateCharacter> candidates, List<MergeCluster> clusters) {
		var byId = candidates.stream().collect(Collectors.toMap(CandidateCharacter::id, Function.identity()));
		var groups = new LinkedHashMap<String, Group>();
		var assigned = new HashSet<Integer>();
		for (var cluster : clusters) {
			var members = cluster.candidateIds().stream()
					.filter(Objects::nonNull)
					.filter(byId::containsKey)
					.filter(assigned::add)
					.map(byId::get)
					.toList();
			if (!members.isEmpty()) {
				var name = isBlank(cluster.canonicalName()) ? members.getFirst().name() : cluster.canonicalName();
				group(groups, name).members().addAll(members);
			}
		}
		for (var candidate : candidates) {
			if (assigned.add(candidate.id())) {
				group(groups, candidate.name()).members().add(candidate);
			}
		}
		return groups.values().stream().map(group -> toCharacter(bookId, group)).toList();
	}

	private static Group group(Map<String, Group> groups, String name) {
		var canonicalName = name.strip();
		return groups.computeIfAbsent(canonicalName.toLowerCase(Locale.ROOT),
				key -> new Group(canonicalName, new ArrayList<>()));
	}

	private static BookCharacter toCharacter(Long bookId, Group group) {
		var character = BookCharacter.builder()
				.bookId(bookId)
				.canonicalName(group.name())
				.sex(sexOf(group.members()))
				.build();
		var seenAliases = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		seenAliases.add(group.name());
		var observations = new ArrayList<CharacterObservation>();
		for (var member : group.members()) {
			addAlias(character, seenAliases, member.name());
			member.aliases().forEach(alias -> addAlias(character, seenAliases, alias));
			member.observations().forEach(observation -> observations.add(toEntity(observation)));
		}
		observations.sort(BY_OFFSET);
		observations.forEach(character::addObservation);
		character.setFirstOffset(observations.stream()
				.map(CharacterObservation::getStartOffset)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null));
		return character;
	}

	/**
	 * The one known sex the members agree on; UNDEFINED when none is known or the members conflict.
	 */
	private static CharacterSex sexOf(List<CandidateCharacter> members) {
		var known = members.stream()
				.map(CandidateCharacter::sex)
				.filter(sex -> sex != null && sex != CharacterSex.UNDEFINED)
				.distinct()
				.toList();
		return known.size() == 1 ? known.getFirst() : CharacterSex.UNDEFINED;
	}

	private static void addAlias(BookCharacter character, TreeSet<String> seen, String alias) {
		if (!isBlank(alias) && seen.add(alias.strip())) {
			character.addAlias(CharacterAlias.builder().alias(alias.strip()).build());
		}
	}

	private static CharacterObservation toEntity(LocatedObservation observation) {
		return CharacterObservation.builder()
				.kind(observation.kind())
				.category(observation.category())
				.description(observation.description())
				.quote(observation.quote())
				.startOffset(observation.startOffset())
				.endOffset(observation.endOffset())
				.chapterId(observation.chapterId())
				.explicitChange(observation.explicitChange())
				.inferred(observation.inferred())
				.build();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private record Group(String name, List<CandidateCharacter> members) {
	}
}
