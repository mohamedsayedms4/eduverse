package com.eduverse.iam.config;

import com.eduverse.common.enums.Role;
import com.eduverse.iam.model.User;
import com.eduverse.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("No users found. Creating initial ADMIN user...");
            
            User admin = new User();
            admin.setFirstName("Super");
            admin.setLastName("Admin");
            admin.setEmail("admin@eduverse.com");
            admin.setPassword(passwordEncoder.encode("Admin@123")); // User should change this
            admin.setRole(Role.ADMIN);
            admin.setActive(true);
            
            userRepository.save(admin);
            log.info("Initial ADMIN user created: admin@eduverse.com / Admin@123");
        }
    }
}
