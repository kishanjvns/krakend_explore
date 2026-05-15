package com.mediq.repository;

import com.mediq.model.UserEntity;
import com.mediq.model.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    List<UserEntity> findByUserTypeAndActive(UserType userType, boolean active);

    boolean existsByIdAndActive(UUID id, boolean active);
}
