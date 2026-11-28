package com.highlight.repository;

import com.highlight.entity.BookLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookLocationRepository extends JpaRepository<BookLocation, Long> {

	List<BookLocation> findAllByBookIdOrderByFirstOffsetAscIdAsc(Long bookId);

	List<BookLocation> findAllByBookId(Long bookId);
}
