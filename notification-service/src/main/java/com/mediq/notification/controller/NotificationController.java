package com.mediq.notification.controller;

import com.mediq.notification.model.NotificationEntity;
import com.mediq.notification.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<NotificationEntity>> getNotificationsForUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(notificationRepository.findByRecipientUserId(userId));
    }
}
