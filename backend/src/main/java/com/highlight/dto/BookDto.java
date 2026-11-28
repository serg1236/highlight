package com.highlight.dto;

import com.highlight.entity.BookFormat;
import com.highlight.entity.BookStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookDto {

	private Long id;

	private String title;

	private String author;

	private String originalFilename;

	private BookFormat format;

	private String language;

	private BookStatus status;

	private int charCount;

	private int chapterCount;

	private Integer chunksTotal;

	private int chunksDone;

	private long inputTokens;

	private long outputTokens;

	private BigDecimal costUsd;

	private String errorMessage;

	private Instant createdAt;

	private Instant updatedAt;
}
