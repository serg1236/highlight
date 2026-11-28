package com.highlight.service;

import com.highlight.dto.CharacterDto;
import com.highlight.dto.CharacterStateDto;

import java.util.List;

public interface CharacterQueryService {

	List<CharacterDto> getCharacters(Long bookId);

	CharacterStateDto getStateAt(Long characterId, int offset);
}
