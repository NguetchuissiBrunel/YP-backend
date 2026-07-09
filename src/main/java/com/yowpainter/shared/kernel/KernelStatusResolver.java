package com.yowpainter.shared.kernel;

import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;

import java.util.List;
import java.util.UUID;

public final class KernelStatusResolver {

    private KernelStatusResolver() {
    }

    public static String determineStatusFromKernel(
            Boolean emailVerified,
            String kernelRegStatus,
            String kernelAccountStatus,
            List<KernelAuthPort.KernelOrganizationAccess> organizations,
            UUID actorId
    ) {
        // Rule: organizationId != null AND kernelActorId != null => ACTIVE
        boolean hasActiveOrg = organizations != null 
                && !organizations.isEmpty() 
                && actorId != null;

        if (hasActiveOrg) {
            return "ACTIVE";
        }

        // 1. Email verification check
        if (!Boolean.TRUE.equals(emailVerified)) {
            return "PENDING_EMAIL";
        }

        // 2. Explicit Kernel registrationStatus / accountStatus check
        if (kernelRegStatus != null) {
            String norm = kernelRegStatus.toUpperCase();
            if (norm.contains("REJECT") || norm.contains("REFUS")) {
                return "ORGANIZATION_REJECTED";
            }
            if (norm.contains("SUSPEND")) {
                return "ORGANIZATION_SUSPENDED";
            }
            if (norm.equals("ACTIVE") || norm.equals("APPROVED") || norm.equals("VALIDATED")) {
                if (organizations != null && !organizations.isEmpty() && actorId != null) {
                    return "ACTIVE";
                }
                return "ORGANIZATION_VALIDATION_REQUIRED";
            }
            if (norm.equals("EMAIL_VERIFIED")) {
                return "EMAIL_VERIFIED";
            }
            if (norm.equals("ORGANIZATION_VALIDATION_REQUIRED") 
                    || norm.equals("PENDING_APPROVAL") 
                    || norm.equals("PENDING_ORGANIZATION")) {
                return "ORGANIZATION_VALIDATION_REQUIRED";
            }
        }

        if (kernelAccountStatus != null) {
            String norm = kernelAccountStatus.toUpperCase();
            if (norm.contains("REJECT") || norm.contains("REFUS")) {
                return "ORGANIZATION_REJECTED";
            }
            if (norm.contains("SUSPEND")) {
                return "ORGANIZATION_SUSPENDED";
            }
        }

        // Default fallback after email verified
        return "ORGANIZATION_VALIDATION_REQUIRED";
    }
}
