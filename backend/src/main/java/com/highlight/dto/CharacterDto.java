package com.highlight.dto;

import com.highlight.entity.CharacterSex;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterDto {

	private Long id;

	private String canonicalName;

	private CharacterSex sex;

	private Integer firstOffset;

	private List<String> aliases;

	private List<ObservationDto> observations;
}
