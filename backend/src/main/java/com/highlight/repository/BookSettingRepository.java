package com.highlight.repository;

import com.highlight.entity.BookSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookSettingRepository extends JpaRepository<BookSetting, Long> {
}
