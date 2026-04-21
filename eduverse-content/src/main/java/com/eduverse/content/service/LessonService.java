package com.eduverse.content.service;

import com.eduverse.content.model.Course;
import com.eduverse.content.model.Lesson;
import com.eduverse.content.repository.LessonRepository;
import com.eduverse.content.service.video.VideoProcessingService;
import com.eduverse.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LessonService {

    private final LessonRepository lessonRepository;
    private final CourseService courseService;
    private final VideoProcessingService videoProcessingService;

    public List<Lesson> getLessonsByCourse(Long courseId) {
        courseService.getCourseById(courseId);
        return lessonRepository.findByCourseIdOrderByOrderIndex(courseId);
    }

    public Lesson getLessonById(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        
        if (!lesson.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Access Denied");
        }
        return lesson;
    }

    @Transactional
    public Lesson createLesson(Long courseId, Lesson lesson) {
        Course course = courseService.getCourseById(courseId);
        lesson.setCourse(course);
        lesson.setTenantId(TenantContext.getCurrentTenant());
        
        // Set video type based on what was provided
        if (lesson.getYoutubeUrl() != null && !lesson.getYoutubeUrl().isBlank()) {
            lesson.setVideoType("YOUTUBE");
            lesson.setVideoStatus("READY");
        }
        
        return lessonRepository.save(lesson);
    }

    @Transactional
    public Lesson uploadVideo(Long lessonId, MultipartFile file) {
        Lesson lesson = getLessonById(lessonId);
        String tenantId = TenantContext.getCurrentTenant();

        try {
            lesson.setVideoType("SELF_HOSTED");
            lesson.setVideoStatus("PROCESSING");
            lesson.setOriginalFileName(file.getOriginalFilename());
            lesson.setYoutubeUrl(null); // Clear any previous YouTube link
            lessonRepository.save(lesson);

            // Save file to disk
            Path outputDir = videoProcessingService.saveUploadedFile(file, tenantId, lessonId);

            // Kick off async transcoding (runs in background)
            videoProcessingService.transcodeToHLS(lessonId, outputDir, tenantId);

            return lesson;
        } catch (Exception e) {
            log.error("Failed to upload video for lesson " + lessonId, e);
            lesson.setVideoStatus("FAILED");
            lessonRepository.save(lesson);
            throw new RuntimeException("Video upload failed: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteLesson(Long id) {
        Lesson lesson = getLessonById(id);
        lessonRepository.delete(lesson);
    }
}

