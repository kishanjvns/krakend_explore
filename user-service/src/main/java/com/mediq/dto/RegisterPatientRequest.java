package com.mediq.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record RegisterPatientRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull LocalDate dateOfBirth,
    @NotEmpty @Valid List<ContactRequest> contacts,
    @Valid List<AddressRequest> addresses,
    @NotBlank String password
) {}
