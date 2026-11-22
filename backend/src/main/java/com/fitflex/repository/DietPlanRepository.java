package com.fitflex.repository;

import com.fitflex.entity.DietPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Database access for saved {@link DietPlan}s. */
public interface DietPlanRepository extends JpaRepository<DietPlan, Long> {

    List<DietPlan> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<DietPlan> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
