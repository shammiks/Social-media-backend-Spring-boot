package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.model.Notification;
import com.example.DPMHC_backend.model.NotificationType;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.NotificationRepository;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public Notification sendNotification(String email, String message, NotificationType type) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Notification notification = Notification.builder()
                .message(message)
                .user(user)
                .isRead(false)
                .createdAt(new Date())
                .type(type)  // set notification type
                .build();

        return notificationRepository.save(notification);
    }

    public List<Notification> getNotifications(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return notificationRepository.findAllByUserOrderByCreatedAtDesc(user);
    }

    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public void markAsUnread(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        notification.setRead(false);
        notificationRepository.save(notification);
    }
}
