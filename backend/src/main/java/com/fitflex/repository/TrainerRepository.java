package com.fitflex.repository;

import com.fitflex.entity.Trainer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Database access for {@link Trainer} profiles, including the public search/filter query. */
public interface TrainerRepository extends JpaRepository<Trainer, Long> {

    Optional<Trainer> findByUserId(Long userId);

    /**
     * Public trainer search. Every filter is optional: passing null skips that condition.
     * JOIN FETCH loads the linked User in the same query (avoids one extra query per trainer).
     * The name filter arrives already lower-cased and wrapped in % from the service.
     */
    @Query("""
            SELECT t FROM Trainer t JOIN FETCH t.user u
            WHERE (:specialization IS NULL OR LOWER(t.specialization) = :specialization)
              AND (:minExperience IS NULL OR t.experienceYears >= :minExperience)
              AND (:maxPrice IS NULL OR t.pricePerSession <= :maxPrice)
              AND (:namePattern IS NULL OR LOWER(u.fullName) LIKE :namePattern)
            ORDER BY t.rating DESC, t.id ASC
            """)
    List<Trainer> search(@Param("specialization") String specialization,
                         @Param("minExperience") Integer minExperience,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("namePattern") String namePattern);
}
