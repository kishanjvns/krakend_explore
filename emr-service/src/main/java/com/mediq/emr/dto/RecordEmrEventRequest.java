package com.mediq.emr.dto;

import jakarta.validation.constraints.NotBlank;

public record RecordEmrEventRequest(
        @NotBlank String payload,
        String recordedBy
) {}
