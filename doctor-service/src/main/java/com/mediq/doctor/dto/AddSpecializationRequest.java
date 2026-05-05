package com.mediq.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddSpecializationRequest(
        @NotBlank(message = "Specialization must not be blank")
        @Size(max = 100, message = "Specialization must be at most 100 characters")
        String specialization,
        boolean primary
) {}
