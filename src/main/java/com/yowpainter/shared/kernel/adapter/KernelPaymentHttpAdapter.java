package com.yowpainter.shared.kernel.adapter;

import com.yowpainter.shared.kernel.KernelHttpClient;
import com.yowpainter.shared.kernel.adapter.dto.*;
import com.yowpainter.shared.kernel.port.KernelPaymentPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class KernelPaymentHttpAdapter implements KernelPaymentPort {

    private final KernelHttpClient kernelHttpClient;

    public KernelPaymentHttpAdapter(KernelHttpClient kernelHttpClient) {
        this.kernelHttpClient = kernelHttpClient;
    }

    // Le controleur kernel BillingLegacyPaymentsController renvoie un PaymentView
    // brut (aucune enveloppe ApiResponse) : on utilise donc les variantes raw.
    // Cf. KernelHttpClient#postRaw.

    @Override
    public KernelPaymentResponseDto createPayment(KernelCreatePaymentRequestDto command, UUID organizationId, String accessToken) {
        return kernelHttpClient.postRaw(
                "/api/paiement",
                command,
                KernelPaymentResponseDto.class,
                organizationId,
                accessToken
        );
    }

    @Override
    public KernelPaymentResponseDto updatePayment(UUID paymentId, KernelUpdatePaymentRequestDto command, UUID organizationId, String accessToken) {
        return kernelHttpClient.putRaw(
                "/api/paiement/" + paymentId,
                command,
                KernelPaymentResponseDto.class,
                organizationId,
                accessToken
        );
    }

    @Override
    public KernelPaymentResponseDto getPayment(UUID paymentId, String accessToken) {
        return kernelHttpClient.getRaw(
                "/api/paiement/" + paymentId,
                KernelPaymentResponseDto.class,
                null,
                accessToken
        );
    }

    @Override
    public List<KernelPaymentResponseDto> listPayments(UUID organizationId, String accessToken) {
        return kernelHttpClient.getRawList(
                "/api/paiement",
                KernelPaymentResponseDto.class,
                organizationId,
                accessToken
        );
    }

    @Override
    public List<KernelPaymentResponseDto> listPaymentsByCustomer(UUID clientId, UUID organizationId, String accessToken) {
        return kernelHttpClient.getRawList(
                "/api/paiement/client/" + clientId,
                KernelPaymentResponseDto.class,
                organizationId,
                accessToken
        );
    }

    @Override
    public List<KernelPaymentResponseDto> listPaymentsByInvoice(UUID invoiceId, UUID organizationId, String accessToken) {
        return kernelHttpClient.getRawList(
                "/api/paiement/facture/" + invoiceId,
                KernelPaymentResponseDto.class,
                organizationId,
                accessToken
        );
    }
}
