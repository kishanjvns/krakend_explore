package com.trucare.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Java 17 FEATURE — Sealed Interface for Referral Status
 *
 * Same pattern as PatientStatus in patient-service.
 * Consistent use of sealed types across both services is intentional —
 * in a real polyglot project you would keep the same domain modelling
 * discipline regardless of which service you're working in.
 *
 * Interview note:
 *   Sealed types are particularly valuable in healthcare systems where
 *   business rules depend on status — e.g. "only PENDING referrals can
 *   be approved", "COMPLETED referrals are read-only". Each variant can
 *   encode its own transition rules as methods.
 */
public sealed interface ReferralStatus
        permits ReferralStatus.Pending,
                ReferralStatus.Approved,
                ReferralStatus.Completed,
                ReferralStatus.Rejected {

    @JsonValue
    String value();

    /** Whether this referral still requires clinical action. */
    boolean isOpen();

    static ReferralStatus from(String raw) {
        return switch (raw.toLowerCase()) {
            case "pending"   -> new Pending();
            case "approved"  -> new Approved();
            case "completed" -> new Completed();
            case "rejected"  -> new Rejected();
            default -> throw new IllegalArgumentException(
                    "Unknown referral status: " + raw);
        };
    }

    record Pending()   implements ReferralStatus {
        @Override public String  value()  { return "pending";   }
        @Override public boolean isOpen() { return true;        }
    }

    record Approved()  implements ReferralStatus {
        @Override public String  value()  { return "approved";  }
        @Override public boolean isOpen() { return true;        }
    }

    record Completed() implements ReferralStatus {
        @Override public String  value()  { return "completed"; }
        @Override public boolean isOpen() { return false;       }
    }

    record Rejected()  implements ReferralStatus {
        @Override public String  value()  { return "rejected";  }
        @Override public boolean isOpen() { return false;       }
    }
}
