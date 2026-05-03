package com.mediq.dto;

import com.mediq.model.VerificationStatus;
import java.time.LocalDate;
import java.util.UUID;

public record DoctorProfileResponse(UUID id, String licenseNumber,
    LocalDate licenseExpiry, int yearsOfExperience,
    VerificationStatus verificationStatus) {}
