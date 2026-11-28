package com.highlight.dto;

import com.highlight.entity.ObservationCategory;
import com.highlight.entity.ObservationKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservationDto {

	private Long id;

	private ObservationKind kind;

	private ObservationCategory category;

	private String description;

	private String quote;

	private Integer startOffset;

	private Integer endOffset;

	private Long chapterId;

	private boolean explicitChange;

	private boolean inferred;
}
