package com.highlight.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A place where scenes of the book happen, with one merged visual description.
 */
@Entity
@Table(name = "book_location")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookLocation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long bookId;

	@Column(nullable = false)
	private String name;

	private String visualDescription;

	/**
	 * Start of the earliest located scene at this place; null when none could be located.
	 */
	private Integer firstOffset;
}
