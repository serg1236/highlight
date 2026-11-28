package com.highlight.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "book_chapter")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookChapter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long bookId;

	private int chapterIndex;

	private String title;

	private int startOffset;

	private int endOffset;
}
