package com.yowpainter.modules.notification.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.notification.domain.model.Notification;
import com.yowpainter.shared.context.RequestContext;
import com.yowpainter.shared.kernel.port.KernelNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final AppUserRepositoryPort userRepository;
    private final KernelNotificationPort kernelNotificationPort;

    public List<Notification> getNotificationsForUser(String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouve"));

        UUID kernelUserId = user.getKernelUserId();
        if (kernelUserId == null) {
            return List.of();
        }

        UUID organizationId = resolveOrganizationId(user);
        List<KernelNotificationPort.DeliveryView> deliveries = kernelNotificationPort.listDeliveries(
                organizationId,
                RequestContext.accessToken()
        );

        return deliveries.stream()
                .filter(delivery -> kernelUserId.equals(delivery.recipientUserId()))
                .map(delivery -> mapToNotification(delivery, user.getId()))
                .toList();
    }

    public void createNotification(UUID userId, String message) {
        createNotification(userId, message, null);
    }

    public void createNotification(UUID userId, String message, UUID organizationId) {
        userRepository.findById(userId).ifPresent(user -> {
            UUID kernelUserId = user.getKernelUserId();
            if (kernelUserId == null) {
                log.warn("Notification ignoree: kernelUserId manquant pour l'utilisateur {}", userId);
                return;
            }
            UUID resolvedOrganizationId = organizationId != null
                    ? organizationId
                    : resolveOrganizationId(user);
            try {
                kernelNotificationPort.send(new KernelNotificationPort.SendNotificationCommand(
                        resolvedOrganizationId,
                        kernelUserId,
                        user.getEmail(),
                        "WEBSOCKET",
                        "YOWPAINTER_GENERIC",
                        "YowPainter",
                        message,
                        Map.of("message", message)
                ), RequestContext.accessToken());
            } catch (Exception ex) {
                log.warn("Echec envoi notification kernel pour l'utilisateur {}: {}", userId, ex.getMessage());
            }
        });
    }

    public void markAsRead(UUID notificationId) {
        log.debug("markAsRead non supporte par le kernel pour la notification {}", notificationId);
    }

    public void markAllAsRead(String email) {
        log.debug("markAllAsRead non supporte par le kernel pour {}", email);
    }

    private UUID resolveOrganizationId(AppUser user) {
        if (user instanceof Artist artist) {
            return artist.getOrganizationId();
        }
        return null;
    }

    private Notification mapToNotification(KernelNotificationPort.DeliveryView delivery, UUID localUserId) {
        String message = delivery.body() != null && !delivery.body().isBlank()
                ? delivery.body()
                : delivery.subject();
        LocalDateTime createdAt = delivery.requestedAt() == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(delivery.requestedAt(), ZoneId.systemDefault());
        return Notification.builder()
                .id(delivery.id())
                .userId(localUserId)
                .message(message)
                .isRead(false)
                .createdAt(createdAt)
                .build();
    }
}
