package com.fitflex.service;

import com.fitflex.exception.BadRequestException;
import com.fitflex.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Saves uploaded files to the local {@code uploads/} folder and reads them back.
 * Files are stored under a random UUID name, never the name the user sent, so an uploaded
 * name like "../../config.txt" can never escape the folder or overwrite another file.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path root;

    public FileStorageService(@Value("${fitflex.upload.dir:uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create upload folder " + root, e);
        }
        log.info("Uploaded files are stored in {}", root);
    }

    /** Copies the upload to disk and returns the generated file name (e.g. "3f2a...c1.pdf"). */
    public String store(MultipartFile file, String extension) {
        String storedName = UUID.randomUUID() + "." + extension;
        Path target = resolveSafely(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the uploaded file", e);
        }
        return storedName;
    }

    public Resource load(String storedName) {
        Path path = resolveSafely(storedName);
        if (!Files.isReadable(path)) {
            throw new ResourceNotFoundException("The file is missing on the server");
        }
        return new FileSystemResource(path);
    }

    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolveSafely(storedName));
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", storedName, e.getMessage());
        }
    }

    /** Extra safety net: refuse any path that would end up outside the upload folder. */
    private Path resolveSafely(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw new BadRequestException("Invalid file path");
        }
        return path;
    }
}
