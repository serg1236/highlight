package com.highlight.repository;

import com.highlight.entity.BookCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookCharacterRepository extends JpaRepository<BookCharacter, Long> {

	List<BookCharacter> findAllByBookIdOrderByFirstOffsetAscIdAsc(Long bookId);

	List<BookCharacter> findAllByBookId(Long bookId);
}
