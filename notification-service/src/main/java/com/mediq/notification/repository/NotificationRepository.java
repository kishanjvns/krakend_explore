package com.mediq.notification.repository;

import com.mediq.notification.model.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<NotificationEntity> findByRecipientUserId(UUID recipientUserId);
}
