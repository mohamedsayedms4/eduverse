package com.eduverse.iam.service;

import com.eduverse.iam.model.RefreshToken;
import com.eduverse.iam.model.User;
import com.eduverse.iam.repository.RefreshTokenRepository;
import com.eduverse.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Value("${eduverse.jwt.refresh-token-expiry:604800000}") // 7 days
    private long refreshTokenDurationMs;

    public User register(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public String generateAccessToken(User user) {
        return jwtService.generateToken(user);
    }

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // Delete old token if exists
        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    public Authentication authenticate(String email, String password) {
        return authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new RuntimeException("No authenticated user found");
        }
        return (User) authentication.getPrincipal();
    }

    @Transactional
    public User createUser(User newUser, User creator) {
        // 1. Validate Roles Hierarchy
        if (creator.getRole() == com.eduverse.common.enums.Role.TEACHER) {
            // Teachers can only create Assistant, Student, Parent
            if (newUser.getRole() == com.eduverse.common.enums.Role.ADMIN || 
                newUser.getRole() == com.eduverse.common.enums.Role.TEACHER) {
                throw new RuntimeException("Teachers cannot create Admins or other Teachers");
            }
            // Inherit tenantId from teacher
            newUser.setTenantId(creator.getTenantId());
        } else if (creator.getRole() == com.eduverse.common.enums.Role.ADMIN) {
            // Admin can create anyone, but must provide tenantId if role is not Admin
            if (newUser.getRole() != com.eduverse.common.enums.Role.ADMIN && newUser.getTenantId() == null) {
                throw new RuntimeException("TenantId is required for non-admin users");
            }
        } else {
            throw new RuntimeException("You do not have permission to create users");
        }

        // 2. Check if email already exists
        if (userRepository.existsByEmail(newUser.getEmail())) {
            throw new RuntimeException("Email already exists: " + newUser.getEmail());
        }

        newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
        return userRepository.save(newUser);
    }

    public java.util.List<User> getUsersForCreator(User creator) {
        if (creator.getRole() == com.eduverse.common.enums.Role.ADMIN) {
            return userRepository.findAll();
        } else {
            // Return users in the same tenant
            return userRepository.findAll().stream()
                    .filter(u -> creator.getTenantId().equals(u.getTenantId()))
                    .toList();
        }
    }
}
