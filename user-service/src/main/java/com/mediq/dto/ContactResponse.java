package com.mediq.dto;

import com.mediq.model.ContactType;
import java.util.UUID;

public record ContactResponse(UUID id, ContactType contactType,
    String contactValue, boolean isPrimary, boolean isVerified) {}
