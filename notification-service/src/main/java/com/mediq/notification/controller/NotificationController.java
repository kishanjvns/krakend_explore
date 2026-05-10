package com.mediq.notification.controller;

import com.mediq.notification.model.NotificationEntity;
import com.mediq.notification.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("(hasAuthority('READ_OWN_NOTIFICATIONS') and #userId.toString() == authentication.principal) or hasAuthority('READ_ANY_NOTIFICATIONS')")
    public ResponseEntity<List<NotificationEntity>> getNotificationsForUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(notificationRepository.findByRecipientUserId(userId));
    }
}
