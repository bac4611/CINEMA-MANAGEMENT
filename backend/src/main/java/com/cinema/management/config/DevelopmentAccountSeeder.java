package com.cinema.management.config;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.repository.account.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.List;

@Configuration
@Profile("dev")
public class DevelopmentAccountSeeder {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentAccountSeeder.class);
    private static final String TEST_PASSWORD = "Password!1";

    @Bean
    ApplicationRunner seedDevelopmentAccounts(UserRepository users) {
        return arguments -> {
            var passwords = new BCryptPasswordEncoder();
            List<TestAccount> accounts = List.of(
                    new TestAccount("admin@gmail.com", "Quản trị viên", "0900000001", "Admin"),
                    new TestAccount("staff@gmail.com", "Nhân viên", "0900000002", "Staff"),
                    new TestAccount("khach@gmail.com", "Khách hàng", "0900000003", "KhachHang"));

            for (TestAccount account : accounts) {
                User user = users.findByNormalizedEmail(account.email()).orElseGet(User::new);
                user.setEmail(account.email());
                user.setFullName(account.fullName());
                user.setPhoneNumber(account.phoneNumber());
                user.setDateOfBirth(LocalDate.of(2000, 1, 1));
                user.setPasswordHash(passwords.encode(TEST_PASSWORD));
                user.setRole(account.role());
                user.setStatus(true);
                user.setEmailConfirmed(true);
                users.saveAndFlush(user);
            }

            log.warn("Development authentication is active. Seeded Admin, Staff and KhachHang test accounts.");
        };
    }

    private record TestAccount(String email, String fullName, String phoneNumber, String role) { }
}
