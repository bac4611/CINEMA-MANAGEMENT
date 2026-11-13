package com.cinema.management.repository.account;

import com.cinema.management.model.entity.account.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository extends JpaRepository<User, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findForUpdate(@Param("userId") Integer userId);

    @Query("select u from User u where lower(trim(u.email)) = :email")
    Optional<User> findByNormalizedEmail(@Param("email") String email);

    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByExternalProviderIgnoreCaseAndExternalProviderKey(
            String externalProvider, String externalProviderKey);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndUserIdNot(String email, Integer userId);
    Optional<User> findByUserIdAndRole(Integer userId, String role);
    Page<User> findByRoleOrderByUserId(String role, Pageable pageable);
}

