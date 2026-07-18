package com.yowpainter.shared.kernel.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelGeneratedWalletResponseDto(
    UUID id,
    UUID organizationId,
    String label,
    String publicKey,
    String privateKey,
    String fingerprint,
    Instant createdAt
) {}
