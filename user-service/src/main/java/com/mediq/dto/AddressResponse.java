package com.mediq.dto;

import com.mediq.model.AddressType;
import java.util.UUID;

public record AddressResponse(UUID id, AddressType addressType,
    String addressLine1, String addressLine2,
    String city, String state, String country, String zip, boolean isPrimary) {}
