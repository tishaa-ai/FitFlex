package com.fitflex.repository;

import com.fitflex.entity.TrainerResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Database access for files uploaded by trainers. */
public interface TrainerResourceRepository extends JpaRepository<TrainerResource, Long> {

    List<TrainerResource> findByTrainerIdOrderByUploadedAtDesc(Long trainerId);

    /** All resources from the given trainers; the service then keeps the ones meant for this user. */
    List<TrainerResource> findByTrainerIdInOrderByUploadedAtDesc(Collection<Long> trainerIds);
}
