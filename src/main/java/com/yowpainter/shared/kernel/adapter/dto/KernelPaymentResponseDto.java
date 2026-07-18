package com.yowpainter.shared.kernel.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelPaymentResponseDto(
    UUID id,
    UUID organizationId,
    UUID billingDocumentId,
    UUID invoiceId,
    UUID supplierInvoiceId,
    String reference,
    BigDecimal amount,
    String currency,
    String status,
    Instant paidAt
) {}
