package com.fitflex.repository;

import com.fitflex.entity.WorkoutPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Database access for {@link WorkoutPlan}s. Exercises are saved through the plan (cascade). */
public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {

    List<WorkoutPlan> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WorkoutPlan> findByTrainerIdOrderByCreatedAtDesc(Long trainerId);
}
