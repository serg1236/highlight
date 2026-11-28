package com.highlight.dto;

import com.highlight.entity.TimeOfDay;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneDto {

	private Long id;

	private int sceneIndex;

	private Long chapterId;

	private Integer startOffset;

	private Integer endOffset;

	private String summary;

	private boolean hasDialog;

	private SceneLocationDto location;

	private String locationQuote;

	private TimeOfDay timeOfDay;

	private String timeMarker;

	private boolean timelineShift;

	private String timelineNote;

	private List<SceneCharacterDto> characters;
}
