package com.highlight.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * One scene of the book: continuous action in one place and time.
 */
@Entity
@Table(name = "book_scene")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookScene {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long bookId;

	/**
	 * Position of the scene in reading order, starting at 0.
	 */
	private int sceneIndex;

	private Long chapterId;

	/**
	 * Start of the scene in the book text; null when its start quote could not be located.
	 */
	private Integer startOffset;

	private Integer endOffset;

	@Column(nullable = false)
	private String summary;

	private boolean hasDialog;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "location_id")
	private BookLocation location;

	private String locationQuote;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TimeOfDay timeOfDay = TimeOfDay.UNKNOWN;

	private String timeMarker;

	private boolean timelineShift;

	private String timelineNote;

	@Builder.Default
	@OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<SceneCharacter> characters = new ArrayList<>();

	public void addCharacter(SceneCharacter character) {
		character.setScene(this);
		characters.add(character);
	}
}
