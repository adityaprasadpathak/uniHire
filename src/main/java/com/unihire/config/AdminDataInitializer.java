package com.unihire.config;

import com.unihire.models.Role;
import com.unihire.models.User;
import com.unihire.repositories.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
/**
 * Component responsible for initializing default admin data during application startup.
 * Ensures that a default superadmin user exists in the database for initial system access.
 */
@Component
public class AdminDataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Listens for the ApplicationReadyEvent to seed the initial admin user.
     * Checks if an admin user with the default email already exists; if not,
     * creates a new admin user with a default password.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedAdminUser() {
        String adminEmail = "admin@unihire.com";
        Optional<User> existingAdmin = userRepository.findByEmail(adminEmail);
        
        if (existingAdmin.isEmpty()) {
            User admin = User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ADMIN)
                    .isLocked(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            userRepository.save(admin);
            System.out.println("Default Superadmin created: " + adminEmail + " / admin123");
        }

        String employerEmail = "employer@unihire.com";
        if (userRepository.findByEmail(employerEmail).isEmpty()) {
            User employer = User.builder()
                    .email(employerEmail)
                    .password(passwordEncoder.encode("employer123"))
                    .role(Role.EMPLOYER)
                    .isLocked(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            userRepository.save(employer);
            System.out.println("Default Employer created: " + employerEmail + " / employer123");
        }

        String candidateEmail = "candidate@unihire.com";
        if (userRepository.findByEmail(candidateEmail).isEmpty()) {
            User candidate = User.builder()
                    .email(candidateEmail)
                    .password(passwordEncoder.encode("candidate123"))
                    .role(Role.CANDIDATE)
                    .isLocked(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            userRepository.save(candidate);
            System.out.println("Default Candidate created: " + candidateEmail + " / candidate123");
        }
    }
}
