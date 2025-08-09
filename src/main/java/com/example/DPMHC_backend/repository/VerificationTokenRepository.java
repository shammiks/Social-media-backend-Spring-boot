package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);

    void deleteByUser(User user);
}
