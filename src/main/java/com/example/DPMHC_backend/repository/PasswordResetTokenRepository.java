package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.PasswordResetToken;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByUser(User user);
}
