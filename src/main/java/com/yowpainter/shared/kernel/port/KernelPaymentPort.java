package com.yowpainter.shared.kernel.port;

import com.yowpainter.shared.kernel.adapter.dto.*;
import java.util.List;
import java.util.UUID;

public interface KernelPaymentPort {
    KernelPaymentResponseDto createPayment(KernelCreatePaymentRequestDto command, UUID organizationId, String accessToken);
    KernelPaymentResponseDto updatePayment(UUID paymentId, KernelUpdatePaymentRequestDto command, UUID organizationId, String accessToken);
    KernelPaymentResponseDto getPayment(UUID paymentId, String accessToken);
    List<KernelPaymentResponseDto> listPayments(UUID organizationId, String accessToken);
    List<KernelPaymentResponseDto> listPaymentsByCustomer(UUID clientId, UUID organizationId, String accessToken);
    List<KernelPaymentResponseDto> listPaymentsByInvoice(UUID invoiceId, UUID organizationId, String accessToken);
}
