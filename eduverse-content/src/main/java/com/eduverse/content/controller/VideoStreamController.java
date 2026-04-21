package com.eduverse.content.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/api/videos")
public class VideoStreamController {

    @Value("${eduverse.video.storage-path:./eduverse-videos}")
    private String storagePath;

    /**
     * Serve HLS master playlist, variant playlists, and .ts segments
     */
    @GetMapping("/{tenantId}/{lessonId}/{filename}")
    public ResponseEntity<Resource> streamVideo(
            @PathVariable String tenantId,
            @PathVariable String lessonId,
            @PathVariable String filename) {

        try {
            Path filePath = Paths.get(storagePath, tenantId, lessonId, filename);

            if (!Files.exists(filePath)) {
                log.warn("Video file not found: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath.toFile());

            // Determine content type based on file extension
            String contentType;
            if (filename.endsWith(".m3u8")) {
                contentType = "application/vnd.apple.mpegurl";
            } else if (filename.endsWith(".ts")) {
                contentType = "video/mp2t";
            } else {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .header("Access-Control-Allow-Origin", "*")
                    .body(resource);

        } catch (Exception e) {
            log.error("Error streaming video file: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
