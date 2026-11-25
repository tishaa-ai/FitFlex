package com.fitflex.service;

import com.fitflex.dto.response.GymSearchResponse.Gym;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the last successful gym search for each location in a small JSON file
 * ({@code data/gym-cache.json} by default, gitignored). If every live map service is down,
 * GymService can still show these saved results, clearly labelled with the date they were saved.
 * The file survives restarts, so one successful search before a demo is enough.
 */
@Component
public class GymCacheStore {

    private static final Logger log = LoggerFactory.getLogger(GymCacheStore.class);

    /** One saved search: which provider answered, when, and the gyms it found. */
    public record SavedSearch(String source, Instant savedAt, List<Gym> gyms) {
    }

    private final Path file;
    private final ObjectMapper objectMapper;
    private final Map<String, SavedSearch> entries = new ConcurrentHashMap<>();

    public GymCacheStore(@Value("${fitflex.gym.cache-file:data/gym-cache.json}") String cacheFile, ObjectMapper objectMapper) {
        this.file = Path.of(cacheFile).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        load();
    }

    public Optional<SavedSearch> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    /** Saves in memory and rewrites the file. Failing to write is logged, never fatal. */
    public synchronized void save(String key, SavedSearch search) {
        entries.put(key, search);
        try {
            Files.createDirectories(file.getParent());
            // Write to a temporary file first, then rename: a crash mid-write can't corrupt the cache.
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writeValue(temp.toFile(), entries);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not save gym cache to {}: {}", file, e.getMessage());
        }
    }

    private void load() {
        if (!Files.isReadable(file)) {
            return;
        }
        try {
            Map<String, SavedSearch> saved = objectMapper.readValue(file.toFile(), new TypeReference<Map<String, SavedSearch>>() {
            });
            entries.putAll(saved);
            log.info("Loaded {} saved gym searches from {}", saved.size(), file);
        } catch (RuntimeException e) {
            log.warn("Ignoring unreadable gym cache {}: {}", file, e.getMessage());
        }
    }
}
