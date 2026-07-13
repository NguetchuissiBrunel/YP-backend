package com.yowpainter.modules.artist.domain.model;

import java.util.List;
import java.util.Locale;

public final class ArtistRegistrationStatus {

    public static final String PENDING_EMAIL = "PENDING_EMAIL";
    public static final String PENDING_APPROVAL = "ORGANIZATION_VALIDATION_REQUIRED";
    public static final String LEGACY_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";
    public static final String ACTIVE = "ACTIVE";
    public static final String REJECTED = "REJECTED";

    private static final List<String> PENDING_ADMIN_APPROVAL = List.of(
            PENDING_APPROVAL,
            LEGACY_PENDING_APPROVAL,
            EMAIL_VERIFIED
    );

    private ArtistRegistrationStatus() {
    }

    public static List<String> pendingAdminApprovalStatuses() {
        return PENDING_ADMIN_APPROVAL;
    }

    public static boolean isPendingAdminApproval(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return PENDING_ADMIN_APPROVAL.stream()
                .anyMatch(candidate -> candidate.equalsIgnoreCase(normalized));
    }
}
