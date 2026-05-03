package com.mediq.dto;

import com.mediq.model.ContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContactRequest(
    @NotNull ContactType contactType,
    @NotBlank String contactValue,
    boolean isPrimary
) {}
