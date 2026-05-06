package com.mediq.repository;

import com.mediq.model.UserOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.UUID;

public interface UserOutboxRepository extends JpaRepository<UserOutboxEntity, UUID> {
    // Only for 7-day cleanup — Debezium reads WAL, never polls this table
    @Modifying
    @Query("DELETE FROM UserOutboxEntity o WHERE o.createdAt < :cutoff")
    int deleteByCreatedAtBefore(Instant cutoff);
}
