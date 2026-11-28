package com.highlight.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "book")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Book {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String title;

	private String author;

	@Column(nullable = false)
	private String originalFilename;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookFormat format;

	private String language;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookStatus status;

	private int charCount;

	private Integer chunksTotal;

	private int chunksDone;

	private long inputTokens;

	private long outputTokens;

	@Builder.Default
	@Column(nullable = false)
	private BigDecimal costUsd = BigDecimal.ZERO;

	private String errorMessage;

	@CreationTimestamp
	@Column(updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;
}
