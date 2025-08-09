package com.example.DPMHC_backend.repository;

import com.example.DPMHC_backend.model.Notification;
import com.example.DPMHC_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByUserOrderByCreatedAtDesc(User user);
}
