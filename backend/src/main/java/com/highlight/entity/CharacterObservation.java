package com.highlight.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One appearance or clothing fact about a character, cited to the book text.
 * A later observation of the same kind and category supersedes an earlier one.
 */
@Entity
@Table(name = "character_observation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterObservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "character_id")
	private BookCharacter character;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ObservationKind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ObservationCategory category;

	@Column(nullable = false)
	private String description;

	@Column(nullable = false)
	private String quote;

	/**
	 * Start of the quote in the book text; null when the quote could not be located.
	 */
	private Integer startOffset;

	private Integer endOffset;

	private Long chapterId;

	private boolean explicitChange;

	/**
	 * True when the quote implies the fact rather than stating it (only AGE and ROLE observations).
	 */
	private boolean inferred;
}
