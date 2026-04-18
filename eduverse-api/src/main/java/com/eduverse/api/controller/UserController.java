package com.eduverse.api.controller;

import com.eduverse.api.dto.RegisterRequest;
import com.eduverse.iam.model.User;
import com.eduverse.iam.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<List<User>> getAll() {
        User currentUser = authService.getCurrentUser();
        return ResponseEntity.ok(authService.getUsersForCreator(currentUser));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<User> create(@RequestBody RegisterRequest request) {
        User currentUser = authService.getCurrentUser();
        
        User newUser = new User();
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setEmail(request.getEmail());
        newUser.setPassword(request.getPassword());
        newUser.setRole(request.getRole());
        newUser.setTenantId(request.getTenantId());

        return ResponseEntity.ok(authService.createUser(newUser, currentUser));
    }
}
