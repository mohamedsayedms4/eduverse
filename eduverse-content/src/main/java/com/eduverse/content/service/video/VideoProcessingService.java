package com.eduverse.content.service.video;

import com.eduverse.content.model.Lesson;
import com.eduverse.content.repository.LessonRepository;
import com.eduverse.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingService {

    private final LessonRepository lessonRepository;

    @Value("${eduverse.video.storage-path:./eduverse-videos}")
    private String storagePath;

    @Value("${eduverse.video.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    /**
     * Save the uploaded file to disk and return the output directory path
     */
    public Path saveUploadedFile(MultipartFile file, String tenantId, Long lessonId) throws Exception {
        Path outputDir = Paths.get(storagePath, tenantId, String.valueOf(lessonId));
        Files.createDirectories(outputDir);

        Path originalFile = outputDir.resolve("original.mp4");
        Files.copy(file.getInputStream(), originalFile, StandardCopyOption.REPLACE_EXISTING);

        log.info("Saved uploaded video to: {}", originalFile);
        return outputDir;
    }

    /**
     * Async method: transcode video with FFmpeg into HLS (360p + 720p)
     * tenantId is passed explicitly because TenantContext is thread-local and won't be available in async threads.
     */
    @Async
    public void transcodeToHLS(Long lessonId, Path outputDir, String tenantId) {
        // CRITICAL: Set the tenant context for this async thread
        TenantContext.setCurrentTenant(tenantId);
        
        Path inputFile = outputDir.resolve("original.mp4");
        
        try {
            log.info("Starting FFmpeg transcoding for lesson {} (tenant: {})", lessonId, tenantId);

            // Build the FFmpeg command
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-i");
            command.add(inputFile.toAbsolutePath().toString());
            command.add("-filter_complex");
            command.add("[0:v]split=2[v1][v2];" +
                    "[v1]scale=w=1280:h=720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,format=yuv420p[v720];" +
                    "[v2]scale=w=640:h=360:force_original_aspect_ratio=decrease,pad=640:360:(ow-iw)/2:(oh-ih)/2,format=yuv420p[v360]");

            // 720p stream
            command.add("-map"); command.add("[v720]");
            command.add("-c:v:0"); command.add("libx264");
            command.add("-b:v:0"); command.add("2500k");
            command.add("-maxrate:v:0"); command.add("2675k");
            command.add("-bufsize:v:0"); command.add("3750k");

            // 360p stream
            command.add("-map"); command.add("[v360]");
            command.add("-c:v:1"); command.add("libx264");
            command.add("-b:v:1"); command.add("800k");
            command.add("-maxrate:v:1"); command.add("856k");
            command.add("-bufsize:v:1"); command.add("1200k");

            // Audio for both streams
            command.add("-map"); command.add("a:0?");
            command.add("-c:a:0"); command.add("aac");
            command.add("-b:a:0"); command.add("128k");
            command.add("-map"); command.add("a:0?");
            command.add("-c:a:1"); command.add("aac");
            command.add("-b:a:1"); command.add("128k");

            // GOP and keyframe alignment
            command.add("-g"); command.add("60");
            command.add("-keyint_min"); command.add("60");
            command.add("-sc_threshold"); command.add("0");

            // HLS options
            command.add("-preset"); command.add("veryfast");
            command.add("-hls_list_size"); command.add("0");
            command.add("-hls_time"); command.add("4");
            command.add("-hls_playlist_type"); command.add("vod");
            command.add("-var_stream_map"); command.add("v:0,a:0,name:720p v:1,a:1,name:360p");
            command.add("-master_pl_name"); command.add("master.m3u8");
            command.add("-hls_segment_filename");
            command.add(outputDir.resolve("%v_%03d.ts").toAbsolutePath().toString());
            command.add(outputDir.resolve("%v.m3u8").toAbsolutePath().toString());

            log.info("Executing FFmpeg command...");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read FFmpeg output for logging
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg: {}", line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                // Success: Update lesson status to READY
                Lesson lesson = lessonRepository.findById(lessonId)
                        .orElseThrow(() -> new RuntimeException("Lesson not found after transcoding"));
                lesson.setVideoStatus("READY");
                lesson.setVideoPath(outputDir.getFileName().toString());
                lessonRepository.save(lesson);
                log.info("✅ Transcoding completed successfully for lesson {}", lessonId);

                // Delete original file to save space
                Files.deleteIfExists(inputFile);
                log.info("Deleted original file to save disk space");
            } else {
                throw new RuntimeException("FFmpeg exited with code: " + exitCode);
            }

        } catch (Exception e) {
            log.error("❌ Transcoding failed for lesson {}", lessonId, e);
            // Update status to FAILED
            lessonRepository.findById(lessonId).ifPresent(lesson -> {
                lesson.setVideoStatus("FAILED");
                lessonRepository.save(lesson);
            });
        } finally {
            TenantContext.clear();
        }
    }
}

