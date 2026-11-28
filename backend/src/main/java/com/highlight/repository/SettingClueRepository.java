package com.highlight.repository;

import com.highlight.entity.SettingClue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettingClueRepository extends JpaRepository<SettingClue, Long> {

	List<SettingClue> findAllByBookIdOrderByStartOffsetAscIdAsc(Long bookId);

	List<SettingClue> findAllByBookId(Long bookId);
}
