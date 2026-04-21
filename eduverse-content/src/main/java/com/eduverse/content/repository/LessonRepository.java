package com.eduverse.content.repository;

import com.eduverse.content.model.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByCourseIdOrderByOrderIndex(Long courseId);
    List<Lesson> findByTenantId(String tenantId);
}
