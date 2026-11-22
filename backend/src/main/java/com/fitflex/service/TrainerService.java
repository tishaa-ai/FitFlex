package com.fitflex.service;

import com.fitflex.dto.request.AvailabilityRequest;
import com.fitflex.dto.request.TrainerProfileUpdateRequest;
import com.fitflex.dto.response.AvailabilitySlotResponse;
import com.fitflex.dto.response.TrainerResponse;
import com.fitflex.dto.response.UserProfileResponse;
import com.fitflex.entity.AvailabilitySlot;
import com.fitflex.entity.SessionStatus;
import com.fitflex.entity.Trainer;
import com.fitflex.entity.TrainingSession;
import com.fitflex.entity.User;
import com.fitflex.exception.BadRequestException;
import com.fitflex.exception.ConflictException;
import com.fitflex.exception.ForbiddenException;
import com.fitflex.exception.ResourceNotFoundException;
import com.fitflex.repository.AvailabilitySlotRepository;
import com.fitflex.repository.TrainerRepository;
import com.fitflex.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trainer profiles (public search + the trainer's own profile), weekly availability, and the
 * "who are my clients" logic. A client is a user who has a non-cancelled one-on-one booking
 * with the trainer or has joined one of the trainer's group sessions.
 */
@Service
public class TrainerService {

    private final TrainerRepository trainerRepository;
    private final AvailabilitySlotRepository availabilityRepository;
    private final TrainingSessionRepository sessionRepository;

    public TrainerService(TrainerRepository trainerRepository, AvailabilitySlotRepository availabilityRepository,
                          TrainingSessionRepository sessionRepository) {
        this.trainerRepository = trainerRepository;
        this.availabilityRepository = availabilityRepository;
        this.sessionRepository = sessionRepository;
    }

    // ===================== public =====================

    /** Public search. Blank filters are ignored. */
    @Transactional(readOnly = true)
    public List<TrainerResponse> search(String specialization, Integer minExperience, BigDecimal maxPrice, String name) {
        String spec = isBlank(specialization) ? null : specialization.trim().toLowerCase(Locale.ROOT);
        String namePattern = isBlank(name) ? null : "%" + name.trim().toLowerCase(Locale.ROOT) + "%";
        return trainerRepository.search(spec, minExperience, maxPrice, namePattern).stream()
                .map(TrainerResponse::publicView)
                .toList();
    }

    @Transactional(readOnly = true)
    public TrainerResponse getPublicProfile(Long trainerId) {
        return TrainerResponse.publicView(getTrainerEntity(trainerId));
    }

    // ===================== helpers used by other services =====================

    @Transactional(readOnly = true)
    public Trainer getTrainerEntity(Long trainerId) {
        return trainerRepository.findById(trainerId)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer not found"));
    }

    /** The Trainer profile that belongs to a logged-in trainer's User account. */
    @Transactional(readOnly = true)
    public Trainer getTrainerByUserId(Long userId) {
        return trainerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer profile not found for this account"));
    }

    /** Users who booked (not cancelled) or joined a group session of this trainer, sorted by name. */
    @Transactional(readOnly = true)
    public List<User> getClientEntities(Long trainerId) {
        // LinkedHashMap keyed by id removes duplicates (a user can be in both lists).
        Map<Long, User> clients = new LinkedHashMap<>();
        sessionRepository.findOneOnOneClients(trainerId, SessionStatus.CANCELLED).forEach(u -> clients.put(u.getId(), u));
        sessionRepository.findGroupClients(trainerId, SessionStatus.CANCELLED).forEach(u -> clients.put(u.getId(), u));
        return clients.values().stream()
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isClient(Long trainerId, Long userId) {
        return getClientEntities(trainerId).stream().anyMatch(u -> u.getId().equals(userId));
    }

    /** Ids of every trainer this user is a client of (used to find resources shared with them). */
    @Transactional(readOnly = true)
    public Set<Long> getTrainerIdsForUser(Long userId) {
        return sessionRepository.findAllForUser(userId).stream()
                .filter(s -> s.getStatus() != SessionStatus.CANCELLED)
                .map(TrainingSession::getTrainer)
                .map(Trainer::getId)
                .collect(Collectors.toSet());
    }

    // ===================== the logged-in trainer =====================

    @Transactional(readOnly = true)
    public TrainerResponse getMyProfile(Long userId) {
        return TrainerResponse.ownerView(getTrainerByUserId(userId));
    }

    @Transactional
    public TrainerResponse updateMyProfile(Long userId, TrainerProfileUpdateRequest request) {
        Trainer trainer = getTrainerByUserId(userId);
        trainer.getUser().setFullName(request.fullName().trim());
        trainer.getUser().setPhone(request.phone());
        trainer.setSpecialization(request.specialization().trim());
        trainer.setExperienceYears(request.experienceYears());
        trainer.setCertifications(request.certifications());
        trainer.setBio(request.bio());
        trainer.setPricePerSession(request.pricePerSession());
        trainer.setProfileImageUrl(request.profileImageUrl());
        return TrainerResponse.ownerView(trainer);
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> getMyAvailability(Long userId) {
        Trainer trainer = getTrainerByUserId(userId);
        return availabilityRepository.findByTrainerId(trainer.getId()).stream()
                .sorted(AvailabilitySlot.WEEK_ORDER)
                .map(AvailabilitySlotResponse::from)
                .toList();
    }

    @Transactional
    public AvailabilitySlotResponse addAvailability(Long userId, AvailabilityRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BadRequestException("End time must be after start time");
        }
        Trainer trainer = getTrainerByUserId(userId);

        // Two time ranges overlap when each one starts before the other one ends.
        for (AvailabilitySlot existing : availabilityRepository.findByTrainerIdAndDayOfWeek(trainer.getId(), request.dayOfWeek())) {
            boolean overlaps = request.startTime().isBefore(existing.getEndTime())
                    && existing.getStartTime().isBefore(request.endTime());
            if (overlaps) {
                throw new ConflictException("This slot overlaps your existing slot " + existing.getDayOfWeek()
                        + " " + existing.getStartTime() + "-" + existing.getEndTime());
            }
        }

        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setTrainer(trainer);
        slot.setDayOfWeek(request.dayOfWeek());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        return AvailabilitySlotResponse.from(availabilityRepository.save(slot));
    }

    @Transactional
    public void deleteAvailability(Long userId, Long slotId) {
        Trainer trainer = getTrainerByUserId(userId);
        AvailabilitySlot slot = availabilityRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Availability slot not found"));
        if (!slot.getTrainer().getId().equals(trainer.getId())) {
            throw new ForbiddenException("This slot belongs to another trainer");
        }
        trainer.getAvailability().remove(slot); // keep the in-memory collection in sync
        availabilityRepository.delete(slot);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> getMyClients(Long userId) {
        Trainer trainer = getTrainerByUserId(userId);
        return getClientEntities(trainer.getId()).stream().map(UserProfileResponse::from).toList();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
