package com.mediq.repository;

import com.mediq.model.OutboxStatus;
import com.mediq.model.UserOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserOutboxRepository extends JpaRepository<UserOutboxEntity, UUID> {
    List<UserOutboxEntity> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
