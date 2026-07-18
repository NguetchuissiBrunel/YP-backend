package com.yowpainter.shared.kernel.adapter.dto;

import java.util.UUID;

public record KernelSubmitTransactionRequestDto(
    UUID organizationId,
    String chainCode,
    String transactionType,
    String sourceService,
    String sourceReference,
    String payload,
    String payloadHash,
    String senderPublicKey,
    String signature
) {}
