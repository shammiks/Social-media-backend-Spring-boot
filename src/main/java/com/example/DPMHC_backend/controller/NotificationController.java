package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.model.Notification;
import com.example.DPMHC_backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<Notification>> getUserNotifications(@RequestParam String email) {
        List<Notification> notifications = notificationService.getNotifications(email);
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markNotificationAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/unread")
    public ResponseEntity<Void> markNotificationAsUnread(@PathVariable Long id) {
        notificationService.markAsUnread(id);
        return ResponseEntity.ok().build();
    }
}
