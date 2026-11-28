package com.highlight.repository;

import com.highlight.entity.Book;
import com.highlight.entity.BookStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {

	List<Book> findAllByOrderByCreatedAtDesc();

	List<Book> findAllByStatusIn(Collection<BookStatus> statuses);
}
