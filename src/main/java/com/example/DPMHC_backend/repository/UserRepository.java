package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    Optional<Object> findByUsername(String username);

    // OPTIMIZED QUERIES FOR BULK USER OPERATIONS
    
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id IN :userIds")
    java.util.List<User> findUsersByIds(@org.springframework.data.repository.query.Param("userIds") java.util.List<Long> userIds);
    
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.email IN :emails")
    java.util.List<User> findUsersByEmails(@org.springframework.data.repository.query.Param("emails") java.util.List<String> emails);
}