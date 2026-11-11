package com.cinema.management.repository.account;

import com.cinema.management.model.entity.account.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByExternalProviderIgnoreCaseAndExternalProviderKey(
            String externalProvider, String externalProviderKey);
    boolean existsByEmailIgnoreCase(String email);
}

