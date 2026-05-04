package com.mediq.notification.repository;

import com.mediq.notification.model.NotificationDlqEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationDlqRepository extends JpaRepository<NotificationDlqEntity, UUID> {
}
