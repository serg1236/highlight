package com.highlight.service;

import com.highlight.dto.LocationDto;
import com.highlight.dto.SceneDto;
import com.highlight.dto.SettingDto;

import java.util.List;

/**
 * Read access to a book's scenes, locations and world setting.
 */
public interface StoryQueryService {

	/**
	 * @param dialogOnly when true, only scenes in which characters speak
	 */
	List<SceneDto> getScenes(Long bookId, boolean dialogOnly);

	List<LocationDto> getLocations(Long bookId);

	/**
	 * @throws com.highlight.exception.ResourceNotFoundException when the book is unknown or has no setting yet
	 */
	SettingDto getSetting(Long bookId);
}
