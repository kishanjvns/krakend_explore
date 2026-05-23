package com.mediq.notification.controller;

import com.mediq.notification.model.NotificationEntity;
import com.mediq.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("(hasAuthority('READ_OWN_NOTIFICATIONS') and #userId.toString() == authentication.principal) or hasAuthority('READ_ANY_NOTIFICATIONS')")
    public ResponseEntity<List<NotificationEntity>> getNotificationsForUser(@PathVariable UUID userId) {
        log.debug("GET /notifications/user/{}", userId);
        List<NotificationEntity> notifications = notificationRepository.findByRecipientUserId(userId);
        log.debug("GET /notifications/user/{} returning {} records", userId, notifications.size());
        return ResponseEntity.ok(notifications);
    }
}
