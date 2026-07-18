package com.yowpainter.modules.payment.application.service;

import com.yowpainter.config.KernelProperties;
import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.auth.application.service.EmailService;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.event.application.service.EventService;
import com.yowpainter.modules.notification.application.service.NotificationService;
import com.yowpainter.modules.payment.infrastructure.adapter.in.web.dto.PaymentResponse;
import com.yowpainter.modules.shop.application.service.InvoiceService;
import com.yowpainter.modules.shop.application.service.ShopService;
import com.yowpainter.modules.shop.domain.model.OrderStatus;
import com.yowpainter.modules.shop.domain.model.Payment;
import com.yowpainter.modules.shop.domain.model.PaymentStatus;
import com.yowpainter.modules.shop.domain.port.out.OrderRepositoryPort;
import com.yowpainter.modules.shop.domain.port.out.PaymentRepositoryPort;
import com.yowpainter.modules.subscription.application.service.SubscriptionService;
import com.yowpainter.shared.kernel.adapter.dto.KernelInitiatePaymentRequestDto;
import com.yowpainter.shared.kernel.adapter.dto.KernelPaymentOrderResponseDto;
import com.yowpainter.shared.kernel.port.KernelPaymentOrderPort;
import com.yowpainter.shared.tenant.TenantTransactionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PaymentServiceTest {

    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private AppUserRepositoryPort userRepository;
    @Mock private ShopService shopService;
    @Mock private EventService eventService;
    @Mock private WalletService walletService;
    @Mock private KernelPaymentOrderPort kernelPaymentOrderPort;
    @Mock private TenantTransactionExecutor tenantTransactionExecutor;
    @Mock private NotificationService notificationService;
    @Mock private ArtistRepositoryPort artistRepository;
    @Mock private EmailService emailService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private InvoiceService invoiceService;
    @Mock private OrderRepositoryPort orderRepository;

    private PaymentService paymentService;

    private AppUser user;
    private Artist artist;
    private Payment orderPayment;
    private UUID organizationId;

    private static final KernelProperties KERNEL_PROPERTIES = new KernelProperties(
            "http://kernel", "yowpainter-client", "api-key", "tenant", null,
            "COMMERCE", "XAF", null, null, null, null, "yow-painter", false, null, null);

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, userRepository, shopService, eventService, walletService,
                kernelPaymentOrderPort, tenantTransactionExecutor, KERNEL_PROPERTIES,
                notificationService, artistRepository, emailService, subscriptionService,
                invoiceService, orderRepository);
        ReflectionTestUtils.setField(paymentService, "backendUrl", "https://backend.test");
        ReflectionTestUtils.setField(paymentService, "frontendUrl", "https://front.test");
        ReflectionTestUtils.setField(paymentService, "paymentProvider", "MYCOOLPAY");
        ReflectionTestUtils.setField(paymentService, "paymentMethod", "MOBILE_MONEY");

        // TenantTransactionExecutor ouvre normalement une transaction dediee au
        // schema tenant : en test on execute l'action directement.
        when(tenantTransactionExecutor.execute(ArgumentMatchers.<Supplier<Object>>any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(tenantTransactionExecutor).execute(ArgumentMatchers.<Runnable>any());

        organizationId = UUID.randomUUID();

        user = AppUser.builder()
                .firstName("Marie").lastName("Dupont")
                .email("marie@example.com")
                .build();
        user.setId(UUID.randomUUID());

        artist = Artist.builder()
                .firstName("Jean").lastName("Artiste")
                .email("jean@example.com").slug("jean-studio")
                .organizationId(organizationId)
                .build();
        artist.setId(UUID.randomUUID());

        orderPayment = Payment.builder()
                .userId(user.getId())
                .referenceId(UUID.randomUUID())
                .referenceType("ORDER")
                .amount(new BigDecimal("15000"))
                .currency("XAF")
                .status(PaymentStatus.PENDING)
                .kernelOrderId("kernel-order-001")
                .providerReference("psp-ref-001")
                .phoneNumber("237690000001")
                .tenantId("jean-studio")
                .build();
        orderPayment.setId(UUID.randomUUID());

        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(artistRepository.findBySlug("jean-studio")).thenReturn(Optional.of(artist));
        when(userRepository.findByEmail("marie@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private KernelPaymentOrderResponseDto kernelOrder(String status, BigDecimal amount, String currency) {
        return new KernelPaymentOrderResponseDto(
                "kernel-order-001", "tenant", "yowpainter-client", "YOWPAINTER_ORDER",
                amount, currency, "MYCOOLPAY", "MOBILE_MONEY", "237690000001",
                status, "psp-ref-001", null, Instant.now(), Instant.now());
    }

    private void stubSubscription() {
        var sub = mock(com.yowpainter.modules.subscription.domain.model.Subscription.class);
        var plan = mock(com.yowpainter.modules.subscription.domain.model.SubscriptionPlan.class);
        when(plan.getCommissionRate()).thenReturn(new BigDecimal("0.10"));
        when(sub.getPlan()).thenReturn(plan);
        when(subscriptionService.getSubscriptionForArtist(artist.getEmail())).thenReturn(sub);
    }

    // ─── initiatePayment ─────────────────────────────────────────────────────

    @Test
    void initiatePayment_shouldCreateLocalPaymentAndDelegateToKernel() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findByReferenceId(orderId))
                .thenReturn(Optional.empty())            // creation
                .thenReturn(Optional.of(orderPayment));  // rattachement de l'ordre kernel
        when(kernelPaymentOrderPort.initiatePayment(any(), eq(organizationId)))
                .thenReturn(kernelOrder("PENDING", new BigDecimal("15000"), "XAF"));

        PaymentInitiationResult result = paymentService.initiatePayment(
                orderId, "ORDER", new BigDecimal("15000"), "jean-studio",
                "marie@example.com", "237690000001");

        assertThat(result.kernelOrderId()).isEqualTo("kernel-order-001");

        ArgumentCaptor<KernelInitiatePaymentRequestDto> captor =
                ArgumentCaptor.forClass(KernelInitiatePaymentRequestDto.class);
        verify(kernelPaymentOrderPort).initiatePayment(captor.capture(), eq(organizationId));
        KernelInitiatePaymentRequestDto sent = captor.getValue();
        assertThat(sent.amount()).isEqualByComparingTo("15000");
        assertThat(sent.currency()).isEqualTo("XAF");
        assertThat(sent.provider()).isEqualTo("MYCOOLPAY");
        // callbackUrl = page frontend de retour (le navigateur y atterrit après paiement)
        assertThat(sent.callbackUrl()).isEqualTo("https://front.test/payment/return?ref=" + orderId + "&type=ORDER");
        // Cle stable : une relance du checkout ne doit pas creer un 2e encaissement.
        assertThat(sent.idempotencyKey()).isEqualTo("yowpainter-order-" + orderId);
    }

    @Test
    void initiatePayment_whenAlreadyPaid_shouldRefuse() {
        UUID orderId = UUID.randomUUID();
        orderPayment.setStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByReferenceId(orderId)).thenReturn(Optional.of(orderPayment));

        assertThatThrownBy(() -> paymentService.initiatePayment(
                orderId, "ORDER", new BigDecimal("15000"), "jean-studio",
                "marie@example.com", "237690000001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deja ete regle");

        verify(kernelPaymentOrderPort, never()).initiatePayment(any(), any());
    }

    @Test
    void initiatePayment_withInvalidAmount_shouldRefuse() {
        assertThatThrownBy(() -> paymentService.initiatePayment(
                UUID.randomUUID(), "ORDER", BigDecimal.ZERO, "jean-studio",
                "marie@example.com", "237690000001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Montant de paiement invalide");

        verify(kernelPaymentOrderPort, never()).initiatePayment(any(), any());
    }

    // ─── processSuccessfulPayment : le kernel fait foi ───────────────────────

    @Test
    void processSuccessfulPayment_whenKernelConfirms_shouldCreditWalletAndConfirmOrder() {
        stubSubscription();
        when(paymentRepository.findByReferenceId(orderPayment.getReferenceId()))
                .thenReturn(Optional.of(orderPayment));
        when(orderRepository.findById(orderPayment.getReferenceId())).thenReturn(Optional.empty());
        when(kernelPaymentOrderPort.refreshPayment("kernel-order-001", organizationId))
                .thenReturn(kernelOrder("SUCCESS", new BigDecimal("15000"), "XAF"));

        paymentService.processSuccessfulPayment("psp-ref-001", orderPayment.getReferenceId().toString());

        assertThat(orderPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(shopService).updateOrderStatus(orderPayment.getReferenceId(), OrderStatus.PAID);
        verify(walletService).creditWallet(eq(artist), any(BigDecimal.class), any(), any(), anyString());
    }

    /**
     * Le callback est public : un attaquant peut le poster. Le kernel doit avoir
     * le dernier mot.
     */
    @Test
    void processSuccessfulPayment_whenKernelDoesNotConfirm_shouldRejectAndNotCredit() {
        when(paymentRepository.findByReferenceId(orderPayment.getReferenceId()))
                .thenReturn(Optional.of(orderPayment));
        when(kernelPaymentOrderPort.refreshPayment("kernel-order-001", organizationId))
                .thenReturn(kernelOrder("PENDING", new BigDecimal("15000"), "XAF"));

        paymentService.processSuccessfulPayment("psp-ref-001", orderPayment.getReferenceId().toString());

        assertThat(orderPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(walletService, never()).creditWallet(any(), any(), any(), any(), anyString());
        verify(shopService, never()).updateOrderStatus(any(), any());
    }

    @Test
    void processSuccessfulPayment_whenKernelAmountDiffers_shouldRejectAndNotCredit() {
        when(paymentRepository.findByReferenceId(orderPayment.getReferenceId()))
                .thenReturn(Optional.of(orderPayment));
        // Le PSP n'a encaisse que 100 alors que la commande vaut 15000.
        when(kernelPaymentOrderPort.refreshPayment("kernel-order-001", organizationId))
                .thenReturn(kernelOrder("SUCCESS", new BigDecimal("100"), "XAF"));

        paymentService.processSuccessfulPayment("psp-ref-001", orderPayment.getReferenceId().toString());

        assertThat(orderPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(walletService, never()).creditWallet(any(), any(), any(), any(), anyString());
    }

    @Test
    void processSuccessfulPayment_whenKernelUnreachable_shouldRejectAndNotCredit() {
        when(paymentRepository.findByReferenceId(orderPayment.getReferenceId()))
                .thenReturn(Optional.of(orderPayment));
        when(kernelPaymentOrderPort.refreshPayment("kernel-order-001", organizationId))
                .thenThrow(new RuntimeException("kernel down"));

        paymentService.processSuccessfulPayment("psp-ref-001", orderPayment.getReferenceId().toString());

        assertThat(orderPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(walletService, never()).creditWallet(any(), any(), any(), any(), anyString());
    }

    /** Un paiement sans ordre kernel ne peut pas etre verifie : on refuse. */
    @Test
    void processSuccessfulPayment_whenNoKernelOrder_shouldRejectAndNotCredit() {
        orderPayment.setKernelOrderId(null);
        when(paymentRepository.findByReferenceId(orderPayment.getReferenceId()))
                .thenReturn(Optional.of(orderPayment));

        paymentService.processSuccessfulPayment("psp-ref-001", orderPayment.getReferenceId().toString());

        assertThat(orderPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(walletService, never()).creditWallet(any(), any(), any(), any(), anyString());
        verify(kernelPaymentOrderPort, never()).refreshPayment(any(), any());
    }

    @Test
    void processSuccessfulPayment_whenAlreadySucceeded_shouldDoNothing() {
        orderPayment.setStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByReferenceId(orderPayment.getReferenceId()))
                .thenReturn(Optional.of(orderPayment));

        paymentService.processSuccessfulPayment("psp-ref-001", orderPayment.getReferenceId().toString());

        verify(kernelPaymentOrderPort, never()).refreshPayment(any(), any());
        verify(shopService, never()).updateOrderStatus(any(), any());
    }

    @Test
    void processSuccessfulPayment_whenPaymentNotFoundInAnyTenant_shouldThrow() {
        UUID unknown = UUID.randomUUID();
        when(paymentRepository.findByReferenceId(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processSuccessfulPayment("psp-ref-000", unknown.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Paiement non trouve pour la reference");
    }

    // ─── processFailedPayment ────────────────────────────────────────────────

    @Test
    void processFailedPayment_shouldMarkPaymentAsFailed() {
        when(paymentRepository.findByReferenceId(orderPayment.getReferenceId()))
                .thenReturn(Optional.of(orderPayment));

        paymentService.processFailedPayment("psp-ref-001",
                orderPayment.getReferenceId().toString(), "FAILED");

        assertThat(orderPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(notificationService).createNotification(eq(user.getId()), anyString());
    }

    @Test
    void processFailedPayment_whenAlreadySucceeded_shouldDoNothing() {
        orderPayment.setStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByReferenceId(orderPayment.getReferenceId()))
                .thenReturn(Optional.of(orderPayment));

        paymentService.processFailedPayment("psp-ref-001",
                orderPayment.getReferenceId().toString(), "FAILED");

        verify(paymentRepository, never()).save(any());
        verify(notificationService, never()).createNotification(any(), anyString());
    }

    @Test
    void processFailedPayment_whenPaymentNotFound_shouldThrowException() {
        UUID unknownRefId = UUID.randomUUID();
        when(paymentRepository.findByReferenceId(unknownRefId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processFailedPayment(
                "psp-ref-000", unknownRefId.toString(), "FAILED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Paiement non trouve pour la reference");
    }

    // ─── getPaymentHistory ───────────────────────────────────────────────────

    @Test
    void getPaymentHistory_shouldReturnUserPaymentsAcrossTenants() {
        orderPayment.setCreatedAt(java.time.LocalDateTime.now());
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(orderPayment));

        List<PaymentResponse> result = paymentService.getPaymentHistory("marie@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReferenceType()).isEqualTo("ORDER");
    }

    @Test
    void getPaymentHistory_whenUserNotFound_shouldThrowException() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentHistory("unknown@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Utilisateur non trouve");
    }

    @Test
    void getPaymentHistory_whenNoPayments_shouldReturnEmptyList() {
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());

        List<PaymentResponse> result = paymentService.getPaymentHistory("marie@example.com");

        assertThat(result).isEmpty();
    }
}
