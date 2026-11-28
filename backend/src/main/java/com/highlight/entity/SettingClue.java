package com.highlight.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One piece of evidence about the book's setting, cited to the book text.
 */
@Entity
@Table(name = "setting_clue")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingClue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long bookId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SettingClueCategory category;

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
}
