package com.highlight.controller;

import com.highlight.dto.CharacterStateDto;
import com.highlight.service.CharacterQueryService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

	private final CharacterQueryService characterQueryService;

	/**
	 * Appearance, clothing and role of the character as of the given offset in the book text.
	 */
	@GetMapping("/{characterId}/state")
	public CharacterStateDto getState(@PathVariable Long characterId, @RequestParam @Min(0) int offset) {
		return characterQueryService.getStateAt(characterId, offset);
	}
}
