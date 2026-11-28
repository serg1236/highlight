package com.highlight.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A character present in a scene.
 */
@Entity
@Table(name = "scene_character")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneCharacter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "scene_id")
	private BookScene scene;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "character_id")
	private BookCharacter character;

	private boolean speaks;
}
