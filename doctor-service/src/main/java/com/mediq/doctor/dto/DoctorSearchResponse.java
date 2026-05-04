package com.mediq.doctor.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DoctorSearchResponse(
        UUID id,
        UUID userId,
        String fullName,
        String primarySpecialization,
        String[] allSpecializations,
        BigDecimal consultationFee,
        int yearsOfExperience,
        boolean verified,
        String[] availableDays
) {}
