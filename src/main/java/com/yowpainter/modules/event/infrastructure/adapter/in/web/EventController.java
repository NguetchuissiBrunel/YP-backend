package com.yowpainter.modules.event.infrastructure.adapter.in.web;

import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.event.application.service.EventService;
import com.yowpainter.modules.event.infrastructure.adapter.in.web.dto.EventCreateRequest;
import com.yowpainter.modules.event.infrastructure.adapter.in.web.dto.EventResponse;
import com.yowpainter.modules.event.infrastructure.adapter.in.web.dto.ReservationResponse;
import com.yowpainter.modules.event.infrastructure.adapter.in.web.dto.TicketResponse;
import com.yowpainter.modules.payment.application.service.PaymentService;
import com.yowpainter.shared.security.AuthenticatedUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Gestion complete des evenements et reservations")
@Slf4j
public class EventController {

    private final EventService eventService;
    private final PaymentService paymentService;
    private final ArtistRepositoryPort artistRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @GetMapping("/public/events")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lister les evenements a venir")
    public ResponseEntity<List<EventResponse>> getUpcomingEvents() {
        return ResponseEntity.ok(eventService.getUpcomingEvents());
    }

    @GetMapping("/public/artists/{artistId}/events")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lister les événements d'un artiste spécifique par ID")
    public ResponseEntity<List<EventResponse>> getEventsByArtist(@PathVariable UUID artistId) {
        return ResponseEntity.ok(eventService.getEventsByArtistId(artistId));
    }

    @GetMapping("/v1/public/{artistSlug}/events")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lister les événements d'un artiste spécifique par slug")
    public ResponseEntity<List<EventResponse>> getEventsByArtistSlug(@PathVariable String artistSlug) {
        return ResponseEntity.ok(eventService.getEventsByArtistSlug(artistSlug));
    }

    @GetMapping("/public/events/{id}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Voir les details d'un evenement")
    public ResponseEntity<EventResponse> getEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @GetMapping("/public/events/search")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Rechercher des evenements par nom ou lieu")
    public ResponseEntity<List<EventResponse>> searchEvents(@RequestParam String q) {
        return ResponseEntity.ok(eventService.searchEvents(q));
    }

    @PostMapping("/events")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Creer un evenement (Artiste)")
    public ResponseEntity<EventResponse> createEvent(
            Authentication authentication,
            @Valid @RequestBody EventCreateRequest request) {
        log.info("[EventController] createEvent - 1. Entrée dans EventController.createEvent()");
        
        if (request != null) {
            log.info("[EventController] createEvent - 2. DTO reçu: name={}, type={}, startDateTime={}, endDateTime={}, posterUrl={}, location={}, maxCapacity={}, ticketPrice={}",
                    request.getName(), request.getType(), request.getStartDateTime(), request.getEndDateTime(),
                    request.getPosterUrl(), request.getLocation(), request.getMaxCapacity(), request.getTicketPrice());
        } else {
            log.info("[EventController] createEvent - 2. DTO reçu: null");
        }

        String authEmail = null;
        String kernelUserIdStr = null;
        String roleStr = null;
        if (authentication != null) {
            authEmail = authentication.getName();
            Object principal = authentication.getPrincipal();
            if (principal instanceof com.yowpainter.modules.auth.domain.model.AppUser user) {
                authEmail = user.getEmail();
                if (user.getKernelUserId() != null) {
                    kernelUserIdStr = user.getKernelUserId().toString();
                }
                if (user.getRole() != null) {
                    roleStr = user.getRole().name();
                }
            }
            if (roleStr == null) {
                roleStr = authentication.getAuthorities().toString();
            }
            log.info("[EventController] createEvent - 3. Utilisateur authentifié: email={}, kernelUserId={}, rôle={}",
                    authEmail, kernelUserIdStr, roleStr);
        } else {
            log.info("[EventController] createEvent - 3. Utilisateur authentifié: null");
        }

        try {
            String email = authenticatedUserResolver.requireEmail(authentication);
            
            log.info("[EventController] createEvent - Tracing parameters:");
            log.info("- startDateTime reçu: {}", request != null ? request.getStartDateTime() : null);
            log.info("- endDateTime reçu: {}", request != null ? request.getEndDateTime() : null);
            log.info("- Instant.now(): {}", java.time.Instant.now());
            log.info("- LocalDateTime.now(): {}", java.time.LocalDateTime.now());
            log.info("- ZoneId.systemDefault(): {}", java.time.ZoneId.systemDefault());
            log.info("- Clock.systemUTC().instant(): {}", java.time.Clock.systemUTC().instant());

            EventResponse response = eventService.createEvent(email, request);
            log.info("[EventController] createEvent - 10. Succès de la création de l'événement.");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception ex) {
            log.error("[EventController] createEvent - 11. Exception interceptée avec stacktrace complète", ex);
            throw ex;
        }
    }

    @GetMapping("/events/me")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Lister mes événements (Artiste - Dashboard)")
    public ResponseEntity<List<EventResponse>> getMyEvents(Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(eventService.getMyEvents(email));
    }

    @GetMapping("/events/reservations/me")
    @PreAuthorize("hasAnyRole('BUYER', 'ARTIST')")
    @Operation(summary = "Lister mes billets / réservations (Acheteur/Amateur)")
    public ResponseEntity<List<ReservationResponse>> getMyReservations(Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(eventService.getMyReservations(email));
    }

    @PutMapping("/events/{id}")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Modifier un evenement (Artiste proprietaire)")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody EventCreateRequest request) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(eventService.updateEvent(id, email, request));
    }

    @DeleteMapping("/events/{id}")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Annuler/Supprimer un evenement")
    public ResponseEntity<Void> cancelEvent(@PathVariable UUID id, Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        eventService.cancelEvent(id, email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/events/{eventId}/reservations")
    @PreAuthorize("hasAnyRole('BUYER', 'ARTIST')")
    @Operation(summary = "RESERVER une place")
    public ResponseEntity<ReservationResponse> reserveEvent(
            Authentication authentication,
            @PathVariable UUID eventId) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(eventService.reserveEvent(eventId, email));
    }

    @PostMapping("/events/reservations/{id}/checkout")
    @PreAuthorize("hasAnyRole('BUYER', 'ARTIST')")
    @Operation(summary = "Initier le paiement Mobile Money (MOMO/Orange) pour une réservation")
    public ResponseEntity<Map<String, String>> checkoutReservation(
            @PathVariable UUID id,
            @RequestParam String phoneNumber,
            Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);

        ReservationResponse reservation = eventService.getReservationById(id);
        EventResponse event = eventService.getEventById(reservation.getEventId());

        if (event.getTicketPrice().compareTo(java.math.BigDecimal.ZERO) == 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cet événement est gratuit"));
        }

        String tenantId = artistRepository.findById(event.getArtistId())
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"))
                .getSlug();

        String paymentReference = paymentService.initiateMobileMoneyPayment(
                id,
                "RESERVATION",
                event.getTicketPrice(),
                tenantId,
                email,
                phoneNumber
        );

        return ResponseEntity.ok(Map.of("paymentReference", paymentReference));
    }

    @GetMapping("/events/{id}/reservations")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Lister les inscrits (Artiste proprietaire)")
    public ResponseEntity<List<ReservationResponse>> getReservations(
            @PathVariable UUID id,
            Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(eventService.getEventReservations(id, email));
    }

    @PostMapping("/events/tickets/validate")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Valider un billet par QR Code")
    public ResponseEntity<TicketResponse> validateTicket(@RequestParam String qrCodeData) {
        return ResponseEntity.ok(eventService.validateTicket(qrCodeData));
    }

    @GetMapping("/public/events/metadata/locations")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lister les lieux disponibles pour les filtres")
    public ResponseEntity<List<String>> getLocations() {
        return ResponseEntity.ok(eventService.getLocations());
    }
}
