package com.mediq.repository;

import com.mediq.model.UserEntity;
import com.mediq.model.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByKeycloakId(String keycloakId);

    List<UserEntity> findByUserTypeAndActive(UserType userType, boolean active);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.contacts LEFT JOIN FETCH u.addresses WHERE u.id = :id")
    Optional<UserEntity> findByIdWithDetails(UUID id);

    boolean existsByIdAndActive(UUID id, boolean active);
}
