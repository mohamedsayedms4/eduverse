package com.eduverse.api.controller;

import com.eduverse.iam.model.Teacher;
import com.eduverse.iam.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teachers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherRepository teacherRepository;

    @PostMapping
    public Teacher create(@RequestBody Teacher teacher) {
        return teacherRepository.save(teacher);
    }

    @GetMapping
    public List<Teacher> getAll() {
        return teacherRepository.findAll();
    }
}
