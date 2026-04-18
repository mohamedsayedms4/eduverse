package com.eduverse.api.controller;

import com.eduverse.api.dto.*;
import com.eduverse.iam.model.RefreshToken;
import com.eduverse.iam.model.User;
import com.eduverse.iam.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final com.eduverse.tenant.service.TenantService tenantService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        // 1. Global Discovery Logic
        String currentTenant = com.eduverse.tenant.context.TenantContext.getCurrentTenant();
        if ("public".equals(currentTenant)) {
            tenantService.findTenantByAdminEmail(request.getEmail())
                    .ifPresent(tenant -> {
                        com.eduverse.tenant.context.TenantContext.setCurrentTenant(tenant.getTenantId());
                    });
        }

        // 2. Authentication
        Authentication auth = authService.authenticate(request.getEmail(), request.getPassword());
        User user = (User) auth.getPrincipal();

        String accessToken = authService.generateAccessToken(user);
        RefreshToken refreshToken = authService.createRefreshToken(user);

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(user.getTenantId())
                .build());
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> register(@RequestBody RegisterRequest request) {
        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setRole(request.getRole());
        user.setTenantId(request.getTenantId());

        return ResponseEntity.ok(authService.register(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        return authService.findByToken(request.getRefreshToken())
                .map(authService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    // Update context for this request to ensure DB lookup works
                    com.eduverse.tenant.context.TenantContext.setCurrentTenant(user.getTenantId());
                    
                    String accessToken = authService.generateAccessToken(user);
                    return ResponseEntity.ok(AuthResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(request.getRefreshToken())
                            .email(user.getEmail())
                            .role(user.getRole())
                            .tenantId(user.getTenantId())
                            .build());
                })
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
