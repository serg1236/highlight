package com.highlight.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * The book's world as concrete visual facts, synthesized from the setting clues.
 */
@Entity
@Table(name = "book_setting")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookSetting {

	@Id
	private Long bookId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Realism realism;

	private String genre;

	private String period;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EvidenceSource periodSource;

	private String region;

	private String technology;

	private String clothingNorms;

	private String architecture;

	private String visualMood;

	/**
	 * Short visual line for image prompts: period, place, dress, architecture.
	 */
	@Column(nullable = false)
	private String promptLine;
}
