package com.mediq.dto;

import com.mediq.model.VerificationStatus;
import jakarta.validation.constraints.NotNull;

public record DoctorVerificationRequest(
    @NotNull VerificationStatus status,
    String rejectionReason
) {}
