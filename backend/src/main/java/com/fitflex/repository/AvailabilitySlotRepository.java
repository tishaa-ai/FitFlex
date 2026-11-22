package com.fitflex.repository;

import com.fitflex.entity.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

/** Database access for trainers' weekly {@link AvailabilitySlot}s. */
public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {

    List<AvailabilitySlot> findByTrainerId(Long trainerId);

    List<AvailabilitySlot> findByTrainerIdAndDayOfWeek(Long trainerId, DayOfWeek dayOfWeek);
}
