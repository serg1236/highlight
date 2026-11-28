package com.highlight.repository;

import com.highlight.entity.BookChapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookChapterRepository extends JpaRepository<BookChapter, Long> {

	List<BookChapter> findAllByBookIdOrderByChapterIndex(Long bookId);
}
