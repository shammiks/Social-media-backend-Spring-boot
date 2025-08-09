package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<Object> findByUsername(String username);
}