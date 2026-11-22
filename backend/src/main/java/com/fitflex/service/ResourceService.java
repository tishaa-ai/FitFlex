package com.fitflex.service;

import com.fitflex.dto.response.ResourceResponse;
import com.fitflex.entity.ResourceType;
import com.fitflex.entity.Role;
import com.fitflex.entity.Trainer;
import com.fitflex.entity.TrainerResource;
import com.fitflex.entity.User;
import com.fitflex.exception.BadRequestException;
import com.fitflex.exception.ForbiddenException;
import com.fitflex.exception.ResourceNotFoundException;
import com.fitflex.repository.TrainerResourceRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Trainer resources (videos, documents, plans): upload with validation, listing for trainers
 * and their clients, and permission-checked downloads.
 */
@Service
public class ResourceService {

    /** Allowed extensions and the content type we serve them with (we never trust the browser's type). */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "mp4", "video/mp4",
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");

    private final TrainerResourceRepository resourceRepository;
    private final FileStorageService fileStorageService;
    private final TrainerService trainerService;
    private final UserService userService;

    public ResourceService(TrainerResourceRepository resourceRepository, FileStorageService fileStorageService,
                           TrainerService trainerService, UserService userService) {
        this.resourceRepository = resourceRepository;
        this.fileStorageService = fileStorageService;
        this.trainerService = trainerService;
        this.userService = userService;
    }

    /** What the controller needs to stream a download back to the browser. */
    public record DownloadFile(Resource resource, String fileName, String contentType, Long sizeBytes) {
    }

    @Transactional
    public ResourceResponse upload(Long trainerUserId, MultipartFile file, String title, ResourceType type, Long shareWithUserId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please choose a file to upload");
        }
        String safeName = sanitizeFileName(file.getOriginalFilename());
        String extension = extensionOf(safeName);
        if (!ALLOWED_TYPES.containsKey(extension)) {
            throw new BadRequestException("File type not allowed. Allowed: mp4, pdf, docx, jpg, png");
        }

        Trainer trainer = trainerService.getTrainerByUserId(trainerUserId);
        User sharedWith = null;
        if (shareWithUserId != null) {
            sharedWith = userService.getUserEntity(shareWithUserId);
            if (!trainerService.isClient(trainer.getId(), sharedWith.getId())) {
                throw new ForbiddenException("You can only share files with your own clients");
            }
        }

        String storedName = fileStorageService.store(file, extension);

        TrainerResource resource = new TrainerResource();
        resource.setTrainer(trainer);
        resource.setTitle(isBlank(title) ? safeName : title.trim());
        resource.setType(type != null ? type : ("mp4".equals(extension) ? ResourceType.VIDEO : ResourceType.DOCUMENT));
        resource.setOriginalFileName(safeName);
        resource.setStoredPath(storedName);
        resource.setContentType(ALLOWED_TYPES.get(extension));
        resource.setSizeBytes(file.getSize());
        resource.setUser(sharedWith);
        return ResourceResponse.from(resourceRepository.save(resource));
    }

    /** TRAINER: my uploads. USER: files from my trainers shared with all clients or with me personally. */
    @Transactional(readOnly = true)
    public List<ResourceResponse> getMyResources(Long userId, Role role) {
        if (role == Role.TRAINER) {
            Trainer trainer = trainerService.getTrainerByUserId(userId);
            return resourceRepository.findByTrainerIdOrderByUploadedAtDesc(trainer.getId()).stream()
                    .map(ResourceResponse::from).toList();
        }
        Set<Long> myTrainerIds = trainerService.getTrainerIdsForUser(userId);
        if (myTrainerIds.isEmpty()) {
            return List.of();
        }
        return resourceRepository.findByTrainerIdInOrderByUploadedAtDesc(myTrainerIds).stream()
                .filter(r -> r.getUser() == null || r.getUser().getId().equals(userId))
                .map(ResourceResponse::from)
                .toList();
    }

    /** Only the trainer who uploaded the file, or a client it is shared with, may download it. */
    @Transactional(readOnly = true)
    public DownloadFile download(Long userId, Role role, Long resourceId) {
        TrainerResource resource = getResourceEntity(resourceId);
        Trainer owner = resource.getTrainer();

        boolean allowed;
        if (role == Role.TRAINER) {
            allowed = owner.getUser().getId().equals(userId);
        } else {
            boolean sharedWithMe = resource.getUser() == null || resource.getUser().getId().equals(userId);
            allowed = sharedWithMe && trainerService.isClient(owner.getId(), userId);
        }
        if (!allowed) {
            throw new ForbiddenException("You do not have access to this file");
        }

        Resource file = fileStorageService.load(resource.getStoredPath());
        return new DownloadFile(file, resource.getOriginalFileName(), resource.getContentType(), resource.getSizeBytes());
    }

    @Transactional
    public void delete(Long trainerUserId, Long resourceId) {
        TrainerResource resource = getResourceEntity(resourceId);
        if (!resource.getTrainer().getUser().getId().equals(trainerUserId)) {
            throw new ForbiddenException("You can only delete your own files");
        }
        resourceRepository.delete(resource);
        fileStorageService.delete(resource.getStoredPath());
    }

    /**
     * Keeps only the last part of the name (drops any folders like "C:\fakepath\" or "../")
     * and replaces unusual characters with "_". Package-private so it can be unit tested.
     */
    static String sanitizeFileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._\\- ]", "_").trim();
        while (name.startsWith(".")) {
            name = name.substring(1); // no hidden files like ".htaccess"
        }
        if (name.isBlank()) {
            name = "file";
        }
        if (name.length() > 120) {
            String ext = extensionOf(name);
            name = name.substring(0, 110) + (ext.isEmpty() ? "" : "." + ext);
        }
        return name;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private TrainerResource getResourceEntity(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
