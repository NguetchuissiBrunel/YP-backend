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
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
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

    @Transactional
    public EventResponse createEvent(String artistEmail, EventCreateRequest request) {
        Artist artist = artistRepository.findByEmail(artistEmail).orElseThrow();
        Event event = Event.builder()
                .artistId(artist.getId())
                .name(request.getName())
                .description(request.getDescription())
                .posterUrl(request.getPosterUrl())
                .startDateTime(request.getStartDateTime())
                .endDateTime(request.getEndDateTime())
                .location(request.getLocation())
                .type(request.getType())
                .maxCapacity(request.getMaxCapacity())
                .ticketPrice(request.getTicketPrice())
                .status(EventStatus.PUBLISHED)
                .build();

        return mapToResponse(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getUpcomingEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        List<EventResponse> allEvents = new ArrayList<>();
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
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
        return eventRepository.findByArtistId(artistId).stream()
                .filter(e -> e.getStatus() != EventStatus.CANCELLED)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsByArtistSlug(String slug) {
        Artist artist = artistRepository.findBySlug(slug).orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));
        if (artist.getOrganizationId() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            return tenantTransactionExecutor.execute(() ->
                eventRepository.findByArtistId(artist.getId()).stream()
                        .filter(e -> e.getStatus() == EventStatus.PUBLISHED)
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
        LocalDateTime now = LocalDateTime.now();
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

    public ReservationResponse reserveEvent(UUID eventId, String userEmail) {
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
                // Ignore schema issues and keep searching
            } finally {
                OrganizationContext.clear();
            }
        }

        if (targetOrgId == null) {
            throw new IllegalArgumentException("Evénement introuvable");
        }

        try {
            OrganizationContext.setOrganizationId(targetOrgId);
            return tenantTransactionExecutor.execute(() -> {
                Event event = eventRepository.findById(eventId).orElseThrow();
                AppUser user = userRepository.findByEmail(userEmail).orElseThrow();

                if (!event.hasAvailableSeats()) {
                    throw new IllegalStateException("Plus de places disponibles");
                }

                boolean isFree = event.getTicketPrice().compareTo(java.math.BigDecimal.ZERO) == 0;

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
                            .qrCodeData(UUID.randomUUID().toString())
                            .isScanned(false)
                            .build();
                    ticketRepository.save(ticket);
                }

                return mapToReservationResponse(reservation);
            });
        } finally {
            OrganizationContext.clear();
        }
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

                Ticket ticket = Ticket.builder()
                        .reservation(reservation)
                        .qrCodeData(UUID.randomUUID().toString())
                        .isScanned(false)
                        .build();
                ticketRepository.save(ticket);
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
        event.setStartDateTime(request.getStartDateTime());
        event.setEndDateTime(request.getEndDateTime());
        event.setLocation(request.getLocation());
        event.setMaxCapacity(request.getMaxCapacity());
        event.setTicketPrice(request.getTicketPrice());

        return mapToResponse(eventRepository.save(event));
    }

    @Transactional
    public void cancelEvent(UUID id, String artistEmail) {
        Event event = eventRepository.findById(id).orElseThrow();
        Artist artist = artistRepository.findByEmail(artistEmail).orElseThrow();
        if (!event.getArtistId().equals(artist.getId())) throw new IllegalArgumentException("Non autorise");

        event.setStatus(EventStatus.CANCELLED);
        eventRepository.save(event);
    }

    @Transactional
    public TicketResponse validateTicket(String qrCodeData) {
        Ticket ticket = ticketRepository.findByQrCodeData(qrCodeData)
                .orElseThrow(() -> new IllegalArgumentException("Billet invalide"));
        
        if (ticket.isScanned()) {
            throw new IllegalStateException("Ce billet a déjà été scanné le " + ticket.getScannedAt());
        }

        ticket.setScanned(true);
        ticket.setScannedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        return mapToTicketResponse(ticket);
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
                .startDateTime(event.getStartDateTime())
                .endDateTime(event.getEndDateTime())
                .location(event.getLocation())
                .type(event.getType())
                .maxCapacity(event.getMaxCapacity())
                .reservedCount(event.getReservedCount())
                .ticketPrice(event.getTicketPrice())
                .status(event.getStatus())
                .build();
    }

    private ReservationResponse mapToReservationResponse(Reservation res) {
        return ReservationResponse.builder()
                .id(res.getId())
                .eventId(res.getEvent().getId())
                .eventName(res.getEvent().getName())
                .userId(res.getUserId())
                .status(res.getStatus())
                .createdAt(res.getReservedAt())
                .build();
    }

    private TicketResponse mapToTicketResponse(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .reservationId(ticket.getReservation().getId())
                .isScanned(ticket.isScanned())
                .scannedAt(ticket.getScannedAt())
                .build();
    }
}
