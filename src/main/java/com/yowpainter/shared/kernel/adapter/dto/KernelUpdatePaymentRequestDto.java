package com.yowpainter.shared.kernel.adapter.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record KernelUpdatePaymentRequestDto(
    UUID billingDocumentId,
    UUID invoiceId,
    UUID supplierInvoiceId,
    UUID counterpartyThirdPartyId,
    String reference,
    BigDecimal amount,
    String currency,
    String status,
    Instant paidAt
) {}
