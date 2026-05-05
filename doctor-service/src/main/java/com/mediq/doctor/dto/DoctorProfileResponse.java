package com.mediq.doctor.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DoctorProfileResponse(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        int yearsOfExperience,
        BigDecimal consultationFee,
        boolean verified,
        boolean active,
        Instant createdAt
) {}
