package com.mediq.appointment.exception;

import java.util.UUID;

public class SlotNotAvailableException extends RuntimeException {
    public SlotNotAvailableException(UUID slotId) {
        super("Slot is not available: " + slotId);
    }
}
