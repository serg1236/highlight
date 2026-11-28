package com.highlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

/**
 * Normalized full text of a book. All offsets in the schema point into {@link #text}.
 */
@Entity
@Table(name = "book_content")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookContent {

	@Id
	private Long bookId;

	@Column(nullable = false)
	private String text;
}
