package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.PasswordResetToken;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findByUserAndToken(User user, String token);
    List<PasswordResetToken> findByUser(User user);
    
    @Modifying
    @Transactional
    void deleteByUser(User user);
}
