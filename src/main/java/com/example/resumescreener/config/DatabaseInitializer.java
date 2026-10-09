package com.example.resumescreener.config;

import com.example.resumescreener.entity.AdminUser;
import com.example.resumescreener.repository.AdminUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseInitializer(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (adminUserRepository.findByUsername("Sagar").isEmpty()) {
            AdminUser adminUser = new AdminUser();
            adminUser.setUsername("Sagar");
            adminUser.setPassword(passwordEncoder.encode("moktan07"));
            adminUser.setFullName("Sagar");
            adminUserRepository.save(adminUser);
        }
    }
}
