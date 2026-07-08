package com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String email;
    private String firstName;
    private String lastName;
    @JsonProperty("imageURL")
    private String imageUrl;
    private String role;
    private String tenantId;
    private String artistName;
    private String message;
    private UUID kernelUserId;
    private UUID organizationId;
    private List<OrganizationAccessResponse> organizations;
    private Boolean emailVerified;
    private String registrationStatus;
    
    private Boolean canAccessDashboard;
    private String pendingReason;
    private String nextAction;
    private Boolean refreshAllowed;
    private String kernelVerificationUrl;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrganizationAccessResponse {
        private UUID organizationId;
        private String organizationCode;
        private String displayName;
        private List<String> services;
    }
}
