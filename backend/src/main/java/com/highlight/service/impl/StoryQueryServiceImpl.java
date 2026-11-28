package com.highlight.service.impl;

import com.highlight.dto.*;
import com.highlight.entity.*;
import com.highlight.exception.ResourceNotFoundException;
import com.highlight.repository.*;
import com.highlight.service.StoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoryQueryServiceImpl implements StoryQueryService {

	private final BookRepository bookRepository;

	private final BookSceneRepository sceneRepository;

	private final BookLocationRepository locationRepository;

	private final BookSettingRepository settingRepository;

	private final SettingClueRepository clueRepository;

	@Override
	public List<SceneDto> getScenes(Long bookId, boolean dialogOnly) {
		requireBook(bookId);
		var scenes = dialogOnly
				? sceneRepository.findAllByBookIdAndHasDialogTrueOrderBySceneIndex(bookId)
				: sceneRepository.findAllByBookIdOrderBySceneIndex(bookId);
		return scenes.stream().map(StoryQueryServiceImpl::toDto).toList();
	}

	@Override
	public List<LocationDto> getLocations(Long bookId) {
		requireBook(bookId);
		return locationRepository.findAllByBookIdOrderByFirstOffsetAscIdAsc(bookId).stream()
				.map(StoryQueryServiceImpl::toDto)
				.toList();
	}

	@Override
	public SettingDto getSetting(Long bookId) {
		requireBook(bookId);
		var setting = settingRepository.findById(bookId)
				.orElseThrow(() -> new ResourceNotFoundException("Setting of book " + bookId + " not found"));
		var clues = clueRepository.findAllByBookIdOrderByStartOffsetAscIdAsc(bookId);
		return toDto(setting, clues);
	}

	private void requireBook(Long bookId) {
		if (!bookRepository.existsById(bookId)) {
			throw new ResourceNotFoundException("Book " + bookId + " not found");
		}
	}

	private static SceneDto toDto(BookScene scene) {
		var location = scene.getLocation();
		return SceneDto.builder()
				.id(scene.getId())
				.sceneIndex(scene.getSceneIndex())
				.chapterId(scene.getChapterId())
				.startOffset(scene.getStartOffset())
				.endOffset(scene.getEndOffset())
				.summary(scene.getSummary())
				.hasDialog(scene.isHasDialog())
				.location(location == null ? null : SceneLocationDto.builder()
						.id(location.getId())
						.name(location.getName())
						.build())
				.locationQuote(scene.getLocationQuote())
				.timeOfDay(scene.getTimeOfDay())
				.timeMarker(scene.getTimeMarker())
				.timelineShift(scene.isTimelineShift())
				.timelineNote(scene.getTimelineNote())
				.characters(scene.getCharacters().stream().map(StoryQueryServiceImpl::toDto).toList())
				.build();
	}

	private static SceneCharacterDto toDto(SceneCharacter sceneCharacter) {
		return SceneCharacterDto.builder()
				.characterId(sceneCharacter.getCharacter().getId())
				.name(sceneCharacter.getCharacter().getCanonicalName())
				.speaks(sceneCharacter.isSpeaks())
				.build();
	}

	private static LocationDto toDto(BookLocation location) {
		return LocationDto.builder()
				.id(location.getId())
				.name(location.getName())
				.visualDescription(location.getVisualDescription())
				.firstOffset(location.getFirstOffset())
				.build();
	}

	private static SettingDto toDto(BookSetting setting, List<SettingClue> clues) {
		return SettingDto.builder()
				.bookId(setting.getBookId())
				.realism(setting.getRealism())
				.genre(setting.getGenre())
				.period(setting.getPeriod())
				.periodSource(setting.getPeriodSource())
				.region(setting.getRegion())
				.technology(setting.getTechnology())
				.clothingNorms(setting.getClothingNorms())
				.architecture(setting.getArchitecture())
				.visualMood(setting.getVisualMood())
				.promptLine(setting.getPromptLine())
				.clues(clues.stream().map(StoryQueryServiceImpl::toDto).toList())
				.build();
	}

	private static SettingClueDto toDto(SettingClue clue) {
		return SettingClueDto.builder()
				.id(clue.getId())
				.category(clue.getCategory())
				.description(clue.getDescription())
				.quote(clue.getQuote())
				.startOffset(clue.getStartOffset())
				.endOffset(clue.getEndOffset())
				.chapterId(clue.getChapterId())
				.build();
	}
}
