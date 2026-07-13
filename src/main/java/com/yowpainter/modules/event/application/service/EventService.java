package com.yowpainter.modules.event.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.event.infrastructure.adapter.in.web.dto.EventCreateRequest;
import com.yowpainter.modules.event.infrastructure.adapter.in.web.dto.EventResponse;
import com.yowpainter.modules.event.infrastructure.adapter.in.web.dto.ReservationResponse;
import com.yowpainter.modules.event.infrastructure.adapter.in.web.dto.TicketResponse;
import com.yowpainter.modules.event.domain.model.*;
import com.yowpainter.modules.event.domain.port.out.EventRepositoryPort;
import com.yowpainter.modules.event.domain.port.out.ReservationRepositoryPort;
import com.yowpainter.modules.event.domain.port.out.TicketRepositoryPort;
import com.yowpainter.modules.auth.application.service.EmailService;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.notification.application.service.NotificationService;
import com.yowpainter.shared.context.OrganizationContext;
import com.yowpainter.shared.tenant.TenantTransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepositoryPort eventRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final ArtistRepositoryPort artistRepository;
    private final AppUserRepositoryPort userRepository;
    private final TicketRepositoryPort ticketRepository;
    private final TenantTransactionExecutor tenantTransactionExecutor;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Transactional
    public EventResponse createEvent(String artistEmail, EventCreateRequest request) {
        log.info("[EventService] createEvent - 1. Entrée dans EventService.createEvent()");
        if (request != null) {
            log.info("[EventService] createEvent - 2. DTO reçu: name={}, type={}, startDateTime={}, endDateTime={}, posterUrl={}, location={}, maxCapacity={}, ticketPrice={}",
                    request.getName(), request.getType(), request.getStartDateTime(), request.getEndDateTime(),
                    request.getPosterUrl(), request.getLocation(), request.getMaxCapacity(), request.getTicketPrice());
        }

        log.info("[EventService] createEvent - 5. OrganizationContext avant initialisation: {}", OrganizationContext.getOrganizationId());

        Artist artist = artistRepository.findByEmail(artistEmail).orElseThrow(() -> {
            log.error("[EventService] createEvent - Artiste non trouvé pour l'email: {}", artistEmail);
            return new IllegalArgumentException("Artiste introuvable");
        });

        log.info("[EventService] createEvent - 4. Artiste trouvé: id={}, organizationId={}, slug={}", 
                artist.getId(), artist.getOrganizationId(), artist.getSlug());

        log.info("[EventService] createEvent - 6. OrganizationContext après initialisation (ou lookup): {}", OrganizationContext.getOrganizationId());

        log.info("[EventService] createEvent - 7. Création de l'entité Event");
        Event event = Event.builder()
                .artistId(artist.getId())
                .name(request.getName())
                .description(request.getDescription())
                .posterUrl(request.getPosterUrl())
                .startDateTime(request.getStartDateTime() != null ? request.getStartDateTime().atZone(java.time.ZoneOffset.UTC).toLocalDateTime() : null)
                .endDateTime(request.getEndDateTime() != null ? request.getEndDateTime().atZone(java.time.ZoneOffset.UTC).toLocalDateTime() : null)
                .location(request.getLocation())
                .type(request.getType())
                .maxCapacity(request.getMaxCapacity())
                .ticketPrice(request.getTicketPrice())
                .status(EventStatus.PUBLISHED)
                .build();

        log.info("[EventService] createEvent - 8. Toutes les validations métier exécutées dans createEvent() (Aucune validation métier personnalisée supplémentaire)");

        log.info("[EventService] createEvent - 9. Juste avant eventRepository.save()");
        try {
            Event savedEvent = eventRepository.save(event);
            log.info("[EventService] createEvent - 10. Juste après eventRepository.save(). Entité sauvegardée: id={}", savedEvent.getId());
            return mapToResponse(savedEvent);
        } catch (Exception ex) {
            log.error("[EventService] createEvent - 11. Exception interceptée avec stacktrace complète", ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getUpcomingEvents() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        List<Artist> activeArtists = artistRepository.findAllWithValidatedOrganization();
        List<EventResponse> allEvents = new ArrayList<>();
        for (Artist artist : activeArtists) {
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                List<EventResponse> tenantEvents = tenantTransactionExecutor.execute(() -> 
                    eventRepository.findUpcomingEvents(now).stream()
                            .filter(e -> artist.getId().equals(e.getArtistId()))
                            .map(this::mapToResponse)
                            .collect(Collectors.toList())
                );
                allEvents.addAll(tenantEvents);
            } catch (Exception e) {
                log.error("Failed to query upcoming events for tenant {}", artist.getOrganizationId(), e);
            } finally {
                OrganizationContext.clear();
            }
        }
        return allEvents.stream()
                .sorted(Comparator.comparing(EventResponse::getStartDateTime))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsByArtistId(UUID artistId) {
        Artist artist = artistRepository.findById(artistId).orElse(null);
        if (artist == null || artist.getOrganizationId() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            return tenantTransactionExecutor.execute(() ->
                eventRepository.findByArtistId(artistId).stream()
                        .filter(e -> e.getStatus() == EventStatus.PUBLISHED || e.getStatus() == EventStatus.FULL || e.getStatus() == EventStatus.ONGOING)
                        .filter(e -> {
                            LocalDateTime end = e.getEndDateTime() != null ? e.getEndDateTime() : e.getStartDateTime();
                            return end != null && end.isAfter(now);
                        })
                        .map(this::mapToResponse)
                        .collect(Collectors.toList())
            );
        } finally {
            OrganizationContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsByArtistSlug(String slug) {
        Artist artist = artistRepository.findBySlug(slug).orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));
        if (artist.getOrganizationId() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            return tenantTransactionExecutor.execute(() ->
                eventRepository.findByArtistId(artist.getId()).stream()
                        .filter(e -> e.getStatus() == EventStatus.PUBLISHED || e.getStatus() == EventStatus.FULL || e.getStatus() == EventStatus.ONGOING)
                        .filter(e -> {
                            LocalDateTime end = e.getEndDateTime() != null ? e.getEndDateTime() : e.getStartDateTime();
                            return end != null && end.isAfter(now);
                        })
                        .map(this::mapToResponse)
                        .collect(Collectors.toList())
            );
        } finally {
            OrganizationContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getMyEvents(String artistEmail) {
        Artist artist = artistRepository.findByEmail(artistEmail).orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));
        return eventRepository.findByArtistId(artist.getId()).stream()
                .filter(e -> e.getStatus() != EventStatus.CANCELLED)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations(String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouve"));

        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        List<ReservationResponse> allReservations = new ArrayList<>();

        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                List<ReservationResponse> tenantReservations = tenantTransactionExecutor.execute(() ->
                    reservationRepository.findByUserId(user.getId()).stream()
                            .filter(this::isActiveBuyerReservation)
                            .map(this::mapToReservationResponse)
                            .collect(Collectors.toList())
                );
                allReservations.addAll(tenantReservations);
            } catch (Exception e) {
                log.error("Failed to query reservations for tenant {}", artist.getOrganizationId(), e);
            } finally {
                OrganizationContext.clear();
            }
        }

        return allReservations.stream()
                .sorted(Comparator.comparing(ReservationResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID id) {
        if (OrganizationContext.getOrganizationId() != null) {
            return mapToResponse(eventRepository.findById(id).orElseThrow());
        }

        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                java.util.Optional<EventResponse> eventResOpt = tenantTransactionExecutor.execute(() -> {
                    java.util.Optional<Event> eventOpt = eventRepository.findById(id);
                    if (eventOpt.isPresent() && artist.getId().equals(eventOpt.get().getArtistId())) {
                        return java.util.Optional.of(mapToResponse(eventOpt.get()));
                    }
                    return java.util.Optional.empty();
                });
                if (eventResOpt.isPresent()) {
                    return eventResOpt.get();
                }
            } catch (Exception e) {
                // Keep searching
            } finally {
                OrganizationContext.clear();
            }
        }
        throw new IllegalArgumentException("Evénement non trouvé");
    }

    @Transactional(readOnly = true)
    public List<EventResponse> searchEvents(String query) {
        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        List<EventResponse> allEvents = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                List<EventResponse> tenantEvents = tenantTransactionExecutor.execute(() ->
                    eventRepository.searchPublicEvents(query).stream()
                            .filter(e -> artist.getId().equals(e.getArtistId()))
                            .filter(e -> {
                                LocalDateTime end = e.getEndDateTime() != null ? e.getEndDateTime() : e.getStartDateTime();
                                return end != null && end.isAfter(now);
                            })
                            .map(this::mapToResponse)
                            .collect(Collectors.toList())
                );
                allEvents.addAll(tenantEvents);
            } catch (Exception e) {
                // Keep searching
            } finally {
                OrganizationContext.clear();
            }
        }
        return allEvents;
    }

    public ReservationResponse reserveEvent(UUID eventId, UUID userId) {
        UUID targetOrgId = null;
        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                final UUID artistId = artist.getId();
                boolean exists = tenantTransactionExecutor.execute(() -> {
                    java.util.Optional<Event> evOpt = eventRepository.findById(eventId);
                    return evOpt.isPresent() && artistId.equals(evOpt.get().getArtistId());
                });
                if (exists) {
                    targetOrgId = artist.getOrganizationId();
                    break;
                }
            } catch (Exception e) {
                log.debug("Recherche evenement {} dans le tenant {} ignoree: {}", eventId, artist.getOrganizationId(), e.getMessage());
            } finally {
                OrganizationContext.clear();
            }
        }

        if (targetOrgId == null) {
            throw new IllegalArgumentException("Evenement introuvable");
        }

        try {
            OrganizationContext.setOrganizationId(targetOrgId);
            return tenantTransactionExecutor.execute(() -> reserveEventInTenant(eventId, userId));
        } finally {
            OrganizationContext.clear();
        }
    }

    public ReservationResponse reserveEvent(UUID eventId, String userEmail) {
        AppUser user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        return reserveEvent(eventId, user.getId());
    }

    private ReservationResponse reserveEventInTenant(UUID eventId, UUID userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Evenement introuvable"));
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));

        reservationRepository.findActiveByEventIdAndUserId(eventId, user.getId())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Vous etes deja inscrit a cet evenement");
                });

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("Cet evenement a ete annule");
        }

        if (!event.hasAvailableSeats()) {
            throw new IllegalStateException("Plus de places disponibles");
        }

        java.math.BigDecimal ticketPrice = event.getTicketPrice() != null
                ? event.getTicketPrice()
                : java.math.BigDecimal.ZERO;
        boolean isFree = ticketPrice.compareTo(java.math.BigDecimal.ZERO) == 0;

        Reservation reservation = Reservation.builder()
                .event(event)
                .userId(user.getId())
                .status(isFree ? ReservationStatus.CONFIRMED : ReservationStatus.PENDING)
                .build();

        event.setReservedCount(event.getReservedCount() + 1);
        if (event.getMaxCapacity() > 0 && event.getReservedCount() >= event.getMaxCapacity()) {
            event.setStatus(EventStatus.FULL);
        }

        eventRepository.save(event);
        reservation = reservationRepository.save(reservation);

        if (isFree) {
            Ticket ticket = Ticket.builder()
                    .reservation(reservation)
                    .qrCodeData(reservation.getId().toString())
                    .isScanned(false)
                    .build();
            ticketRepository.save(ticket);
        }

        return mapToReservationResponse(reservation, event);
    }

    public void confirmPaidReservation(UUID reservationId) {
        UUID targetOrgId = null;
        if (OrganizationContext.getOrganizationId() != null) {
            targetOrgId = OrganizationContext.getOrganizationId();
        } else {
            List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
            for (Artist artist : activeArtists) {
                if (artist.getOrganizationId() == null) continue;
                try {
                    OrganizationContext.setOrganizationId(artist.getOrganizationId());
                    final UUID artistId = artist.getId();
                    boolean exists = tenantTransactionExecutor.execute(() -> {
                        java.util.Optional<Reservation> resOpt = reservationRepository.findById(reservationId);
                        return resOpt.isPresent() && artistId.equals(resOpt.get().getEvent().getArtistId());
                    });
                    if (exists) {
                        targetOrgId = artist.getOrganizationId();
                        break;
                    }
                } catch (Exception e) {
                    // Keep searching
                } finally {
                    OrganizationContext.clear();
                }
            }
        }

        if (targetOrgId == null) {
            throw new IllegalArgumentException("Reservation non trouvée");
        }

        try {
            OrganizationContext.setOrganizationId(targetOrgId);
            tenantTransactionExecutor.execute(() -> {
                Reservation reservation = reservationRepository.findById(reservationId)
                        .orElseThrow(() -> new IllegalArgumentException("Reservation non trouvée"));

                if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                    return;
                }

                reservation.setStatus(ReservationStatus.CONFIRMED);
                reservationRepository.save(reservation);

                java.util.Optional<Ticket> existingTicket = ticketRepository.findByReservationId(reservation.getId());
                if (existingTicket.isEmpty()) {
                    Ticket ticket = Ticket.builder()
                            .reservation(reservation)
                            .qrCodeData(reservation.getId().toString())
                            .isScanned(false)
                            .build();
                    ticketRepository.save(ticket);
                }
            });
        } finally {
            OrganizationContext.clear();
        }
    }

    public List<ReservationResponse> getEventReservations(UUID eventId, String artistEmail) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        Artist artist = artistRepository.findByEmail(artistEmail).orElseThrow();
        if (!event.getArtistId().equals(artist.getId())) throw new IllegalArgumentException("Non autorise");

        return reservationRepository.findByEventId(eventId).stream()
                .map(this::mapToReservationResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(UUID reservationId) {
        if (OrganizationContext.getOrganizationId() != null) {
            Reservation res = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new IllegalArgumentException("Reservation non trouvée"));
            return mapToReservationResponse(res);
        }

        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                java.util.Optional<ReservationResponse> resOpt = tenantTransactionExecutor.execute(() -> {
                    java.util.Optional<Reservation> rOpt = reservationRepository.findById(reservationId);
                    if (rOpt.isPresent() && artist.getId().equals(rOpt.get().getEvent().getArtistId())) {
                        return java.util.Optional.of(mapToReservationResponse(rOpt.get()));
                    }
                    return java.util.Optional.empty();
                });
                if (resOpt.isPresent()) {
                    return resOpt.get();
                }
            } catch (Exception e) {
                // Keep searching
            } finally {
                OrganizationContext.clear();
            }
        }
        throw new IllegalArgumentException("Reservation non trouvée");
    }

    @Transactional
    public EventResponse updateEvent(UUID id, String artistEmail, EventCreateRequest request) {
        Event event = eventRepository.findById(id).orElseThrow();
        Artist artist = artistRepository.findByEmail(artistEmail).orElseThrow();
        if (!event.getArtistId().equals(artist.getId())) throw new IllegalArgumentException("Non autorise");

        event.setName(request.getName());
        event.setDescription(request.getDescription());
        event.setPosterUrl(request.getPosterUrl());
        event.setStartDateTime(request.getStartDateTime() != null ? request.getStartDateTime().atZone(java.time.ZoneOffset.UTC).toLocalDateTime() : null);
        event.setEndDateTime(request.getEndDateTime() != null ? request.getEndDateTime().atZone(java.time.ZoneOffset.UTC).toLocalDateTime() : null);
        event.setLocation(request.getLocation());
        event.setMaxCapacity(request.getMaxCapacity());
        event.setTicketPrice(request.getTicketPrice());

        return mapToResponse(eventRepository.save(event));
    }

    public void cancelEvent(UUID id, String artistEmail) {
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste introuvable"));
        if (artist.getOrganizationId() == null) {
            throw new IllegalStateException("Organisation artiste non configuree");
        }

        List<EventCancellationNotice> notices;
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            notices = tenantTransactionExecutor.execute(() -> cancelEventInTenant(id, artist));
        } finally {
            OrganizationContext.clear();
        }

        for (EventCancellationNotice notice : notices) {
            notifyBuyerOfEventCancellationSafely(notice, artist);
        }
    }

    private List<EventCancellationNotice> cancelEventInTenant(UUID id, Artist artist) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evenement introuvable"));
        if (!event.getArtistId().equals(artist.getId())) {
            throw new IllegalArgumentException("Non autorise");
        }

        boolean alreadyCancelled = event.getStatus() == EventStatus.CANCELLED;
        String notificationMessage = "L'evenement \"" + event.getName() + "\" a ete annule. Votre reservation n'est plus valide.";
        List<EventCancellationNotice> notices = new ArrayList<>();

        List<Reservation> reservations = reservationRepository.findByEventId(id);
        for (Reservation reservation : reservations) {
            ticketRepository.findByReservationId(reservation.getId()).ifPresent(ticketRepository::delete);

            if (reservation.getStatus() != ReservationStatus.CANCELLED) {
                reservation.setStatus(ReservationStatus.CANCELLED);
                reservationRepository.save(reservation);
                if (!alreadyCancelled) {
                    userRepository.findById(reservation.getUserId()).ifPresent(user ->
                            notices.add(new EventCancellationNotice(
                                    user.getId(),
                                    user.getEmail(),
                                    event.getName(),
                                    notificationMessage
                            ))
                    );
                }
            }
        }

        event.setStatus(EventStatus.CANCELLED);
        event.setReservedCount(0);
        eventRepository.save(event);
        return notices;
    }

    private void notifyBuyerOfEventCancellationSafely(EventCancellationNotice notice, Artist artist) {
        try {
            notificationService.createNotification(
                    notice.userId(),
                    notice.message(),
                    artist.getOrganizationId()
            );
            if (notice.email() != null && !notice.email().isBlank()) {
                emailService.sendEventCancellationEmail(
                        notice.email(),
                        notice.eventName(),
                        artist.getArtistName()
                );
            }
        } catch (Exception ex) {
            log.warn("Notification d'annulation ignoree pour l'utilisateur {}: {}", notice.userId(), ex.getMessage());
        }
    }

    private record EventCancellationNotice(
            UUID userId,
            String email,
            String eventName,
            String message
    ) {}

    @Transactional
    public TicketResponse validateTicket(String qrCodeData) {
        Ticket ticket = ticketRepository.findByQrCodeData(qrCodeData)
                .orElseGet(() -> {
                    try {
                        UUID reservationId = UUID.fromString(qrCodeData);
                        return ticketRepository.findByReservationId(reservationId)
                                .orElseThrow(() -> new IllegalArgumentException("Billet invalide"));
                    } catch (IllegalArgumentException ex) {
                        throw ex;
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Billet invalide");
                    }
                });

        ensureTicketIsValid(ticket);
        
        if (ticket.isScanned()) {
            throw new IllegalStateException("Ce billet a déjà été scanné le " + ticket.getScannedAt());
        }

        ticket.setScanned(true);
        ticket.setScannedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        return mapToTicketResponse(ticket);
    }

    private void ensureTicketIsValid(Ticket ticket) {
        Reservation reservation = ticket.getReservation();
        if (reservation == null) {
            throw new IllegalArgumentException("Billet invalide");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("Ce billet n'est plus valide : la reservation a ete annulee");
        }
        Event event = reservation.getEvent();
        if (event == null) {
            throw new IllegalArgumentException("Billet invalide");
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("Ce billet n'est plus valide : l'evenement a ete annule");
        }
    }

    @Transactional
    public void cancelAbandonedReservations() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        List<Reservation> abandoned = reservationRepository.findByStatusAndReservedAtBefore(ReservationStatus.PENDING, threshold);

        for (Reservation res : abandoned) {
            res.setStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(res);

            Event event = res.getEvent();
            event.setReservedCount(event.getReservedCount() - 1);
            if (event.getStatus() == EventStatus.FULL) {
                event.setStatus(EventStatus.PUBLISHED);
            }
            eventRepository.save(event);

            log.info("Cancelled abandoned reservation: {} for event: {}", res.getId(), event.getName());
        }
    }

    @Transactional(readOnly = true)
    public List<String> getLocations() {
        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        java.util.Set<String> locations = new java.util.HashSet<>();
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                List<String> tenantLocations = tenantTransactionExecutor.execute(() -> 
                    eventRepository.findDistinctLocations()
                );
                locations.addAll(tenantLocations);
            } catch (Exception e) {
                // Ignore and keep searching
            } finally {
                OrganizationContext.clear();
            }
        }
        return new ArrayList<>(locations);
    }

    private EventResponse mapToResponse(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .artistId(event.getArtistId())
                .name(event.getName())
                .description(event.getDescription())
                .posterUrl(com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(event.getPosterUrl()))
                .startDateTime(event.getStartDateTime() != null ? event.getStartDateTime().toInstant(java.time.ZoneOffset.UTC) : null)
                .endDateTime(event.getEndDateTime() != null ? event.getEndDateTime().toInstant(java.time.ZoneOffset.UTC) : null)
                .location(event.getLocation())
                .type(event.getType())
                .maxCapacity(event.getMaxCapacity())
                .reservedCount(event.getReservedCount())
                .ticketPrice(event.getTicketPrice())
                .status(event.getStatus())
                .build();
    }

    private boolean isActiveBuyerReservation(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return false;
        }
        Event event = reservation.getEvent();
        return event == null || event.getStatus() != EventStatus.CANCELLED;
    }

    private ReservationResponse mapToReservationResponse(Reservation res) {
        return mapToReservationResponse(res, res.getEvent());
    }

    private ReservationResponse mapToReservationResponse(Reservation res, Event event) {
        String qrCode = null;
        try {
            java.util.Optional<Ticket> ticketOpt = ticketRepository.findByReservationId(res.getId());
            if (ticketOpt.isPresent()) {
                qrCode = ticketOpt.get().getQrCodeData();
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve ticket for reservation {}", res.getId(), e);
        }

        String userName = null;
        String userEmail = null;
        try {
            AppUser user = userRepository.findById(res.getUserId()).orElse(null);
            if (user != null) {
                userEmail = user.getEmail();
                userName = (user.getFirstName() != null && user.getLastName() != null)
                        ? user.getFirstName() + " " + user.getLastName()
                        : user.getFirstName();
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve user for reservation {}", res.getId(), e);
        }

        UUID eventId = event != null ? event.getId() : null;
        String eventName = event != null ? event.getName() : null;
        String eventLocation = event != null ? event.getLocation() : null;
        java.math.BigDecimal ticketPrice = event != null && event.getTicketPrice() != null
                ? event.getTicketPrice()
                : java.math.BigDecimal.ZERO;

        String resolvedQrCode = resolveQrCodeData(res, event, qrCode);

        return ReservationResponse.builder()
                .id(res.getId())
                .eventId(eventId)
                .eventName(eventName)
                .userId(res.getUserId())
                .userName(userName)
                .userEmail(userEmail)
                .status(res.getStatus())
                .createdAt(res.getReservedAt() != null ? res.getReservedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
                .qrCodeData(resolvedQrCode)
                .eventLocation(eventLocation)
                .ticketPrice(ticketPrice)
                .build();
    }

    private String resolveQrCodeData(Reservation reservation, Event event, String ticketQrCode) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return null;
        }
        if (event != null && event.getStatus() == EventStatus.CANCELLED) {
            return null;
        }
        if (ticketQrCode != null) {
            return ticketQrCode;
        }
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return reservation.getId().toString();
        }
        return null;
    }

    private TicketResponse mapToTicketResponse(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .reservationId(ticket.getReservation().getId())
                .isScanned(ticket.isScanned())
                .scannedAt(ticket.getScannedAt() != null ? ticket.getScannedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
                .build();
    }
}
