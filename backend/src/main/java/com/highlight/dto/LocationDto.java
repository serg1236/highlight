package com.highlight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {

	private Long id;

	private String name;

	private String visualDescription;

	private Integer firstOffset;
}
