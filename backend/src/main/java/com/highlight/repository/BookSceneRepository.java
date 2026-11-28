package com.highlight.repository;

import com.highlight.entity.BookScene;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookSceneRepository extends JpaRepository<BookScene, Long> {

	List<BookScene> findAllByBookIdOrderBySceneIndex(Long bookId);

	List<BookScene> findAllByBookIdAndHasDialogTrueOrderBySceneIndex(Long bookId);

	List<BookScene> findAllByBookId(Long bookId);
}
