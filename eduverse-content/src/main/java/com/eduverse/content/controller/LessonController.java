package com.eduverse.content.controller;

import com.eduverse.content.model.Lesson;
import com.eduverse.content.service.LessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LessonController {

    private final LessonService lessonService;

    @GetMapping("/courses/{id}/lessons")
    public List<Lesson> getLessonsByCourse(@PathVariable Long id) {
        return lessonService.getLessonsByCourse(id);
    }

    @PostMapping("/courses/{id}/lessons")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Lesson createLesson(@PathVariable Long id, @RequestBody Lesson lesson) {
        return lessonService.createLesson(id, lesson);
    }

    @GetMapping("/lessons/{id}")
    public Lesson getLesson(@PathVariable Long id) {
        return lessonService.getLessonById(id);
    }

    @PostMapping("/lessons/{id}/video")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Lesson uploadVideo(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return lessonService.uploadVideo(id, file);
    }

    @GetMapping("/lessons/{id}/status")
    public ResponseEntity<Map<String, String>> getLessonStatus(@PathVariable Long id) {
        Lesson lesson = lessonService.getLessonById(id);
        return ResponseEntity.ok(Map.of(
                "status", lesson.getVideoStatus() != null ? lesson.getVideoStatus() : "NONE",
                "videoType", lesson.getVideoType() != null ? lesson.getVideoType() : "NONE"
        ));
    }

    @DeleteMapping("/lessons/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<Void> deleteLesson(@PathVariable Long id) {
        lessonService.deleteLesson(id);
        return ResponseEntity.noContent().build();
    }
}

