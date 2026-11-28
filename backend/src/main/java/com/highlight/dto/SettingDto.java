package com.highlight.dto;

import com.highlight.entity.EvidenceSource;
import com.highlight.entity.Realism;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The book's world setting with the clues it was synthesized from.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingDto {

	private Long bookId;

	private Realism realism;

	private String genre;

	private String period;

	private EvidenceSource periodSource;

	private String region;

	private String technology;

	private String clothingNorms;

	private String architecture;

	private String visualMood;

	private String promptLine;

	private List<SettingClueDto> clues;
}
