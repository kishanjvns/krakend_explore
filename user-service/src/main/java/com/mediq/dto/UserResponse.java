package com.mediq.dto;

import com.mediq.model.UserType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String keycloakId,
    UserType userType,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    boolean active,
    boolean verified,
    List<ContactResponse> contacts,
    List<AddressResponse> addresses,
    DoctorProfileResponse doctorProfile,
    Instant createdAt
) {}
