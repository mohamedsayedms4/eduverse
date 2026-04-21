package com.eduverse.content.service;

import com.eduverse.content.model.Course;
import com.eduverse.content.repository.CourseRepository;
import com.eduverse.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;

    public List<Course> getMyCourses() {
        String tenantId = TenantContext.getCurrentTenant();
        return courseRepository.findByTenantId(tenantId);
    }

    public Course getCourseById(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        
        if (!course.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Access Denied");
        }
        return course;
    }

    @Transactional
    public Course createCourse(Course course) {
        course.setTenantId(TenantContext.getCurrentTenant());
        return courseRepository.save(course);
    }

    @Transactional
    public Course updateCourse(Long id, Course courseDetails) {
        Course course = getCourseById(id);
        course.setTitle(courseDetails.getTitle());
        course.setDescription(courseDetails.getDescription());
        course.setThumbnailUrl(courseDetails.getThumbnailUrl());
        course.setPublished(courseDetails.isPublished());
        return courseRepository.save(course);
    }

    @Transactional
    public void deleteCourse(Long id) {
        Course course = getCourseById(id);
        courseRepository.delete(course);
    }
}
