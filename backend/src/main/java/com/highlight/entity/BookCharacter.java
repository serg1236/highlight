package com.highlight.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "book_character")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookCharacter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long bookId;

	@Column(nullable = false)
	private String canonicalName;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CharacterSex sex = CharacterSex.UNDEFINED;

	/**
	 * Offset of the earliest located observation; null when none could be located.
	 */
	private Integer firstOffset;

	@Builder.Default
	@OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<CharacterAlias> aliases = new ArrayList<>();

	@Builder.Default
	@OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("startOffset, id")
	private List<CharacterObservation> observations = new ArrayList<>();

	public void addAlias(CharacterAlias alias) {
		alias.setCharacter(this);
		aliases.add(alias);
	}

	public void addObservation(CharacterObservation observation) {
		observation.setCharacter(this);
		observations.add(observation);
	}
}
