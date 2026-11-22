package com.fitflex.repository;

import com.fitflex.entity.ProgressEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/** Database access for {@link ProgressEntry} logs. */
public interface ProgressEntryRepository extends JpaRepository<ProgressEntry, Long> {

    List<ProgressEntry> findByUserIdOrderByDateAscIdAsc(Long userId);

    List<ProgressEntry> findByUserIdAndDateBetweenOrderByDateAscIdAsc(Long userId, LocalDate from, LocalDate to);
}
