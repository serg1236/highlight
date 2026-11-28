package com.highlight.repository;

import com.highlight.entity.BookContent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookContentRepository extends JpaRepository<BookContent, Long> {
}
