package com.yowpainter.modules.payment.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.auth.application.service.EmailService;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.event.application.service.EventService;
import com.yowpainter.modules.notification.application.service.NotificationService;
import com.yowpainter.modules.payment.infrastructure.adapter.in.web.dto.PaymentResponse;
import com.yowpainter.modules.payment.infrastructure.adapter.out.external.PaymentGatewayClient;
import com.yowpainter.modules.shop.application.service.ShopService;
import com.yowpainter.modules.shop.domain.model.OrderStatus;
import com.yowpainter.modules.shop.domain.model.Payment;
import com.yowpainter.modules.shop.domain.model.PaymentStatus;
import com.yowpainter.modules.shop.domain.port.out.PaymentRepositoryPort;
import com.yowpainter.modules.subscription.application.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private AppUserRepositoryPort userRepository;
    @Mock private ShopService shopService;
    @Mock private EventService eventService;
    @Mock private PaymentGatewayClient paymentGatewayClient;
    @Mock private WalletService walletService;
    @Mock private NotificationService notificationService;
    @Mock private ArtistRepositoryPort artistRepository;
    @Mock private EmailService emailService;
    @Mock private SubscriptionService subscriptionService;

    @InjectMocks
    private PaymentService paymentService;

    private AppUser user;
    private Artist artist;
    private Payment orderPayment;
    private Payment reservationPayment;

    @BeforeEach
    void setUp() {
        user = AppUser.builder()
                .firstName("Marie").lastName("Dupont")
                .email("marie@example.com")
                .build();
        user.setId(UUID.randomUUID());

        artist = Artist.builder()
                .firstName("Jean").lastName("Artiste")
                .email("jean@example.com").slug("jean-studio")
                .build();
        artist.setId(UUID.randomUUID());

        orderPayment = Payment.builder()
                .userId(user.getId())
                .referenceId(UUID.randomUUID())
                .referenceType("ORDER")
                .amount(new BigDecimal("15000"))
                .currency("XAF")
                .status(PaymentStatus.PENDING)
                .providerReference("camp-ref-001")
                .phoneNumber("237690000001")
                .tenantId("jean-studio")
                .build();
        orderPayment.setId(UUID.randomUUID());

        reservationPayment = Payment.builder()
                .userId(user.getId())
                .referenceId(UUID.randomUUID())
                .referenceType("RESERVATION")
                .amount(new BigDecimal("5000"))
                .currency("XAF")
                .status(PaymentStatus.PENDING)
                .providerReference("camp-ref-002")
                .phoneNumber("237690000001")
                .tenantId("jean-studio")
                .build();
        reservationPayment.setId(UUID.randomUUID());
    }

    // ─── getPaymentHistory ───────────────────────────────────────────────────

    @Test
    void getPaymentHistory_shouldReturnUserPayments() {
        when(userRepository.findByEmail("marie@example.com")).thenReturn(Optional.of(user));
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(orderPayment, reservationPayment));

        List<PaymentResponse> result = paymentService.getPaymentHistory("marie@example.com");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReferenceType()).isEqualTo("ORDER");
        assertThat(result.get(1).getReferenceType()).isEqualTo("RESERVATION");
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
        when(userRepository.findByEmail("marie@example.com")).thenReturn(Optional.of(user));
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());

        List<PaymentResponse> result = paymentService.getPaymentHistory("marie@example.com");

        assertThat(result).isEmpty();
    }

    // ─── initiateMobileMoneyPayment ──────────────────────────────────────────

    @Test
    void initiateMobileMoneyPayment_shouldThrowUnsupportedOperationException() {
        assertThatThrownBy(() -> paymentService.initiateMobileMoneyPayment(
                UUID.randomUUID(), "ORDER", new BigDecimal("15000"), "jean-studio",
                "marie@example.com", "237690000001"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Le paiement Mobile Money n'est pas encore disponible");
    }

    // ─── processSuccessfulPayment ────────────────────────────────────────────

    @Test
    void processSuccessfulPayment_forOrder_shouldUpdateStatusAndTriggerOrderConfirmation() throws Exception {
        // Setup subscription mock
        com.yowpainter.modules.subscription.domain.model.Subscription sub =
                mock(com.yowpainter.modules.subscription.domain.model.Subscription.class);
        com.yowpainter.modules.subscription.domain.model.SubscriptionPlan plan =
                mock(com.yowpainter.modules.subscription.domain.model.SubscriptionPlan.class);
        when(plan.getCommissionRate()).thenReturn(new BigDecimal("0.10"));
        when(sub.getPlan()).thenReturn(plan);

        when(paymentRepository.findByReferenceId(orderPayment.getReferenceId()))
                .thenReturn(Optional.of(orderPayment));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(artistRepository.findBySlug("jean-studio")).thenReturn(Optional.of(artist));
        when(subscriptionService.getSubscriptionForArtist(artist.getEmail())).thenReturn(sub);

        paymentService.processSuccessfulPayment("camp-ref-001", orderPayment.getReferenceId().toString());

        assertThat(orderPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository).save(orderPayment);
        verify(shopService).updateOrderStatus(orderPayment.getReferenceId(), OrderStatus.PAID);
        verify(notificationService).createNotification(eq(user.getId()), anyString());
        verify(walletService).creditWallet(eq(artist), any(BigDecimal.class), any(), any(), anyString());
    }

    @Test
    void processSuccessfulPayment_forReservation_shouldConfirmReservation() throws Exception {
        com.yowpainter.modules.subscription.domain.model.Subscription sub =
                mock(com.yowpainter.modules.subscription.domain.model.Subscription.class);
        com.yowpainter.modules.subscription.domain.model.SubscriptionPlan plan =
                mock(com.yowpainter.modules.subscription.domain.model.SubscriptionPlan.class);
        when(plan.getCommissionRate()).thenReturn(new BigDecimal("0.10"));
        when(sub.getPlan()).thenReturn(plan);

        when(paymentRepository.findByReferenceId(reservationPayment.getReferenceId()))
                .thenReturn(Optional.of(reservationPayment));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(artistRepository.findBySlug("jean-studio")).thenReturn(Optional.of(artist));
        when(subscriptionService.getSubscriptionForArtist(artist.getEmail())).thenReturn(sub);

        paymentService.processSuccessfulPayment("camp-ref-002", reservationPayment.getReferenceId().toString());

        assertThat(reservationPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(eventService).confirmPaidReservation(reservationPayment.getReferenceId());
    }

    @Test
    void processSuccessfulPayment_whenAlreadySucceeded_shouldDoNothing() throws Exception {
        orderPayment.setStatus(PaymentStatus.SUCCEEDED);

        when(paymentRepository.findByReferenceId(orderPayment.getReferenceId()))
                .thenReturn(Optional.of(orderPayment));

        paymentService.processSuccessfulPayment("camp-ref-001", orderPayment.getReferenceId().toString());

        verify(paymentGatewayClient, never()).getToken();
        verify(shopService, never()).updateOrderStatus(any(), any());
    }

    // ─── processFailedPayment ────────────────────────────────────────────────

    @Test
    void processFailedPayment_shouldMarkPaymentAsFailed() {
        when(paymentRepository.findByReferenceId(orderPayment.getReferenceId()))
                .thenReturn(Optional.of(orderPayment));

        paymentService.processFailedPayment("camp-ref-001",
                orderPayment.getReferenceId().toString(), "FAILED");

        assertThat(orderPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(orderPayment);
        verify(notificationService).createNotification(eq(user.getId()), anyString());
    }

    @Test
    void processFailedPayment_whenAlreadySucceeded_shouldDoNothing() {
        orderPayment.setStatus(PaymentStatus.SUCCEEDED);

        when(paymentRepository.findByReferenceId(orderPayment.getReferenceId()))
                .thenReturn(Optional.of(orderPayment));

        paymentService.processFailedPayment("camp-ref-001",
                orderPayment.getReferenceId().toString(), "FAILED");

        verify(paymentRepository, never()).save(any());
        verify(notificationService, never()).createNotification(any(), anyString());
    }

    @Test
    void processFailedPayment_whenPaymentNotFound_shouldThrowException() {
        UUID unknownRefId = UUID.randomUUID();
        when(paymentRepository.findByReferenceId(unknownRefId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processFailedPayment(
                "camp-ref-000", unknownRefId.toString(), "FAILED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Paiement non trouve pour la reference");
    }
}
