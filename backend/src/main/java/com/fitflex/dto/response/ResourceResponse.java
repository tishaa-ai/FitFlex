package com.fitflex.dto.response;

import com.fitflex.entity.ResourceType;
import com.fitflex.entity.TrainerResource;

import java.time.LocalDateTime;

/**
 * Metadata of an uploaded trainer file. The file is fetched separately from
 * GET /api/resources/{id}/download. {@code sharedWithUserId} null means "all my clients".
 */
public record ResourceResponse(
        Long id,
        String title,
        ResourceType type,
        String originalFileName,
        String contentType,
        Long sizeBytes,
        LocalDateTime uploadedAt,
        Long trainerId,
        String trainerName,
        Long sharedWithUserId,
        String sharedWithUserName) {

    public static ResourceResponse from(TrainerResource r) {
        return new ResourceResponse(r.getId(), r.getTitle(), r.getType(), r.getOriginalFileName(),
                r.getContentType(), r.getSizeBytes(), r.getUploadedAt(),
                r.getTrainer().getId(), r.getTrainer().getUser().getFullName(),
                r.getUser() != null ? r.getUser().getId() : null,
                r.getUser() != null ? r.getUser().getFullName() : null);
    }
}
