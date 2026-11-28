package com.highlight.dto;

import com.highlight.entity.SettingClueCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingClueDto {

	private Long id;

	private SettingClueCategory category;

	private String description;

	private String quote;

	private Integer startOffset;

	private Integer endOffset;

	private Long chapterId;
}
