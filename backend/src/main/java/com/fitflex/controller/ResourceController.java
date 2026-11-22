package com.fitflex.controller;

import com.fitflex.dto.response.ResourceResponse;
import com.fitflex.entity.ResourceType;
import com.fitflex.security.UserPrincipal;
import com.fitflex.service.ResourceService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Trainer resources: multipart upload (TRAINER), listing, permission-checked download,
 * and delete. Upload size is limited to 50 MB in application.properties.
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /** multipart/form-data fields: file (required), title, type (VIDEO/DOCUMENT/PLAN), userId (optional). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse upload(@AuthenticationPrincipal UserPrincipal me,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String title,
                                   @RequestParam(required = false) ResourceType type,
                                   @RequestParam(required = false) Long userId) {
        return resourceService.upload(me.getId(), file, title, type, userId);
    }

    @GetMapping("/my")
    public List<ResourceResponse> myResources(@AuthenticationPrincipal UserPrincipal me) {
        return resourceService.getMyResources(me.getId(), me.getRole());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        ResourceService.DownloadFile file = resourceService.download(me.getId(), me.getRole(), id);
        // "attachment" tells the browser to save the file; the UTF-8 form keeps non-English names intact.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .body(file.resource());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TRAINER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        resourceService.delete(me.getId(), id);
    }
}
