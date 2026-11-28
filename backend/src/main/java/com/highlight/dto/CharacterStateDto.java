package com.highlight.dto;

import com.highlight.entity.CharacterSex;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Latest appearance, clothing and role observation per category at or before {@link #offset}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterStateDto {

	private Long characterId;

	private String canonicalName;

	private CharacterSex sex;

	private int offset;

	private List<ObservationDto> appearance;

	private List<ObservationDto> clothing;

	private List<ObservationDto> role;
}
