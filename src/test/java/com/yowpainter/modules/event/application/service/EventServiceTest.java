package com.yowpainter.modules.event.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.event.domain.model.*;
import com.yowpainter.modules.event.domain.port.out.EventRepositoryPort;
import com.yowpainter.modules.event.domain.port.out.ReservationRepositoryPort;
import com.yowpainter.modules.event.domain.port.out.TicketRepositoryPort;
import com.yowpainter.modules.auth.application.service.EmailService;
import com.yowpainter.modules.notification.application.service.NotificationService;
import com.yowpainter.modules.event.infrastructure.adapter.in.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import com.yowpainter.shared.tenant.TenantTransactionExecutor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EventServiceTest {

    @Mock
    private EventRepositoryPort eventRepository;

    @Mock
    private ReservationRepositoryPort reservationRepository;

    @Mock
    private ArtistRepositoryPort artistRepository;

    @Mock
    private AppUserRepositoryPort userRepository;

    @Mock
    private TicketRepositoryPort ticketRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TenantTransactionExecutor tenantTransactionExecutor;

    @Mock
    private NotificationService notificationService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EventService eventService;

    private Artist artist;
    private Event event;
    private AppUser user;
    private Reservation reservation;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        lenient().when(tenantTransactionExecutor.execute(any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(0);
                    return supplier.get();
                });
        lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(tenantTransactionExecutor).execute(any(Runnable.class));

        lenient().when(reservationRepository.findActiveByEventIdAndUserId(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.empty());
        lenient().when(userRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            if (user.getId().equals(id)) {
                return Optional.of(user);
            }
            return Optional.empty();
        });

        artist = Artist.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .slug("john-doe")
                .organizationId(UUID.randomUUID())
                .build();
        artist.setId(UUID.randomUUID());

        event = Event.builder()
                .artistId(artist.getId())
                .name("Vernissage d'Art")
                .description("Une belle exposition")
                .startDateTime(LocalDateTime.now().plusDays(2))
                .endDateTime(LocalDateTime.now().plusDays(2).plusHours(3))
                .location("Paris")
                .type(EventType.MEETUP)
                .maxCapacity(10)
                .reservedCount(0)
                .ticketPrice(BigDecimal.ZERO)
                .status(EventStatus.PUBLISHED)
                .build();
        event.setId(UUID.randomUUID());

        user = AppUser.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .build();
        user.setId(UUID.randomUUID());

        reservation = Reservation.builder()
                .event(event)
                .userId(user.getId())
                .status(ReservationStatus.PENDING)
                .reservedAt(LocalDateTime.now())
                .build();
        reservation.setId(UUID.randomUUID());

        ticket = Ticket.builder()
                .reservation(reservation)
                .qrCodeData(UUID.randomUUID().toString())
                .isScanned(false)
                .build();
        ticket.setId(UUID.randomUUID());
    }

    @Test
    void createEvent_shouldSaveEventAndReturnResponse() {
        EventCreateRequest request = EventCreateRequest.builder()
                .name("Vernissage d'Art")
                .description("Une belle exposition")
                .startDateTime(event.getStartDateTime().toInstant(java.time.ZoneOffset.UTC))
                .endDateTime(event.getEndDateTime().toInstant(java.time.ZoneOffset.UTC))
                .location("Paris")
                .type(EventType.MEETUP)
                .maxCapacity(10)
                .ticketPrice(BigDecimal.ZERO)
                .build();

        when(artistRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(artist));
        when(eventRepository.save(any(Event.class))).thenReturn(event);

        EventResponse response = eventService.createEvent("john.doe@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo(request.getName());
        assertThat(response.getArtistId()).isEqualTo(artist.getId());
        assertThat(response.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void getEventsByArtistId_shouldReturnList() {
        UUID artistId = artist.getId();
        when(artistRepository.findById(artistId)).thenReturn(Optional.of(artist));
        when(eventRepository.findByArtistId(artistId)).thenReturn(List.of(event));

        List<EventResponse> result = eventService.getEventsByArtistId(artistId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Vernissage d'Art");
    }

    @Test
    void getEventsByArtistSlug_shouldReturnPublishedEvents() {
        when(artistRepository.findBySlug("john-doe")).thenReturn(Optional.of(artist));
        when(eventRepository.findByArtistId(artist.getId())).thenReturn(List.of(event));

        List<EventResponse> result = eventService.getEventsByArtistSlug("john-doe");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Vernissage d'Art");
    }

    @Test
    void getMyEvents_shouldReturnAllEventsForArtist() {
        when(artistRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(artist));
        when(eventRepository.findByArtistId(artist.getId())).thenReturn(List.of(event));

        List<EventResponse> result = eventService.getMyEvents("john.doe@example.com");

        assertThat(result).hasSize(1);
    }

    @Test
    void getEventById_shouldReturnResponse() {
        UUID eventId = event.getId();
        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        EventResponse response = eventService.getEventById(eventId);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Vernissage d'Art");
    }

    @Test
    void reserveEvent_forFreeEvent_shouldConfirmReservationAndCreateTicket() {
        event.setTicketPrice(BigDecimal.ZERO);
        event.setMaxCapacity(10);
        event.setReservedCount(0);

        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(eventRepository.save(any(Event.class))).thenReturn(event);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation res = invocation.getArgument(0);
            res.setId(UUID.randomUUID());
            return res;
        });

        ReservationResponse response = eventService.reserveEvent(event.getId(), "alice@example.com");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(event.getReservedCount()).isEqualTo(1);

        verify(ticketRepository).save(any(Ticket.class));
        verify(eventRepository).save(event);
    }

    @Test
    void reserveEvent_forPaidEvent_shouldCreatePendingReservationWithoutTicket() {
        event.setTicketPrice(BigDecimal.TEN);
        event.setMaxCapacity(10);
        event.setReservedCount(0);

        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(eventRepository.save(any(Event.class))).thenReturn(event);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation res = invocation.getArgument(0);
            res.setId(UUID.randomUUID());
            return res;
        });

        ReservationResponse response = eventService.reserveEvent(event.getId(), "alice@example.com");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(event.getReservedCount()).isEqualTo(1);

        verify(ticketRepository, never()).save(any(Ticket.class));
        verify(eventRepository).save(event);
    }

    @Test
    void reserveEvent_whenEventFull_shouldThrowException() {
        event.setMaxCapacity(1);
        event.setReservedCount(1);

        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> eventService.reserveEvent(event.getId(), "alice@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Plus de places disponibles");

        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void reserveEvent_whenUserAlreadyRegistered_shouldRejectDuplicate() {
        event.setTicketPrice(BigDecimal.ZERO);
        event.setMaxCapacity(10);
        event.setReservedCount(1);

        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(reservationRepository.findActiveByEventIdAndUserId(event.getId(), user.getId()))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> eventService.reserveEvent(event.getId(), "alice@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deja inscrit");

        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(ticketRepository, never()).save(any(Ticket.class));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void reserveEvent_whenPreviousReservationCancelled_shouldAllowNewRegistration() {
        event.setTicketPrice(BigDecimal.ZERO);
        event.setMaxCapacity(10);
        event.setReservedCount(0);

        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(reservationRepository.findActiveByEventIdAndUserId(event.getId(), user.getId()))
                .thenReturn(Optional.empty());
        when(eventRepository.save(any(Event.class))).thenReturn(event);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation res = invocation.getArgument(0);
            res.setId(UUID.randomUUID());
            return res;
        });

        ReservationResponse response = eventService.reserveEvent(event.getId(), "alice@example.com");

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void confirmPaidReservation_shouldUpdateStatusAndCreateTicket() {
        reservation.setStatus(ReservationStatus.PENDING);

        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        eventService.confirmPaidReservation(reservation.getId());

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository).save(reservation);
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void confirmPaidReservation_whenAlreadyConfirmed_shouldDoNothing() {
        reservation.setStatus(ReservationStatus.CONFIRMED);

        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        eventService.confirmPaidReservation(reservation.getId());

        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void getEventReservations_shouldReturnReservations() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(artistRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(artist));
        when(reservationRepository.findByEventId(event.getId())).thenReturn(List.of(reservation));

        List<ReservationResponse> result = eventService.getEventReservations(event.getId(), "john.doe@example.com");

        assertThat(result).hasSize(1);
    }

    @Test
    void getReservationById_shouldReturnResponse() {
        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        ReservationResponse result = eventService.getReservationById(reservation.getId());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(reservation.getId());
    }

    @Test
    void updateEvent_shouldModifyFieldsAndSave() {
        EventCreateRequest request = EventCreateRequest.builder()
                .name("Nouveau Nom")
                .description("Nouvelle desc")
                .startDateTime(java.time.Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS))
                .endDateTime(java.time.Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS).plus(2, java.time.temporal.ChronoUnit.HOURS))
                .location("Lyon")
                .maxCapacity(20)
                .ticketPrice(BigDecimal.ONE)
                .build();

        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(artistRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(artist));
        when(eventRepository.save(any(Event.class))).thenReturn(event);

        EventResponse response = eventService.updateEvent(event.getId(), "john.doe@example.com", request);

        assertThat(response).isNotNull();
        assertThat(event.getName()).isEqualTo("Nouveau Nom");
        assertThat(event.getDescription()).isEqualTo("Nouvelle desc");
        assertThat(event.getLocation()).isEqualTo("Lyon");
        verify(eventRepository).save(event);
    }

    @Test
    void cancelEvent_shouldCancelReservationsDeleteTicketsAndNotifyBuyers() {
        reservation.setStatus(ReservationStatus.CONFIRMED);
        event.setReservedCount(1);

        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(artistRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(artist));
        when(reservationRepository.findByEventId(event.getId())).thenReturn(List.of(reservation));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(ticketRepository.findByReservationId(reservation.getId())).thenReturn(Optional.of(ticket));

        eventService.cancelEvent(event.getId(), "john.doe@example.com");

        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
        assertThat(event.getReservedCount()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(ticketRepository).delete(ticket);
        verify(reservationRepository).save(reservation);
        verify(notificationService).createNotification(eq(user.getId()), anyString(), eq(artist.getOrganizationId()));
        verify(emailService).sendEventCancellationEmail(user.getEmail(), event.getName(), artist.getArtistName());
        verify(eventRepository).save(event);
    }

    @Test
    void cancelEvent_whenEmailFails_shouldStillCancelEvent() {
        reservation.setStatus(ReservationStatus.CONFIRMED);
        event.setReservedCount(1);

        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(artistRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(artist));
        when(reservationRepository.findByEventId(event.getId())).thenReturn(List.of(reservation));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(ticketRepository.findByReservationId(reservation.getId())).thenReturn(Optional.of(ticket));
        doThrow(new RuntimeException("SMTP indisponible"))
                .when(emailService).sendEventCancellationEmail(any(), any(), any());

        eventService.cancelEvent(event.getId(), "john.doe@example.com");

        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
        verify(eventRepository).save(event);
    }

    @Test
    void cancelEvent_whenArtistHasNoOrganization_shouldReject() {
        artist.setOrganizationId(null);
        when(artistRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(artist));

        assertThatThrownBy(() -> eventService.cancelEvent(event.getId(), "john.doe@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Organisation");

        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void reserveEvent_whenEventCancelled_shouldRejectRegistration() {
        event.setStatus(EventStatus.CANCELLED);

        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> eventService.reserveEvent(event.getId(), "alice@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("annule");

        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void validateTicket_whenEventCancelled_shouldRejectScan() {
        event.setStatus(EventStatus.CANCELLED);
        reservation.setStatus(ReservationStatus.CANCELLED);
        ticket.setScanned(false);

        when(ticketRepository.findByQrCodeData("qrcode-123")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> eventService.validateTicket("qrcode-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("annule");

        verify(ticketRepository, never()).save(ticket);
    }

    @Test
    void cancelEvent_shouldSetStatusToCancelled() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(artistRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(artist));
        when(reservationRepository.findByEventId(event.getId())).thenReturn(List.of());

        eventService.cancelEvent(event.getId(), "john.doe@example.com");

        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
        verify(eventRepository).save(event);
    }

    @Test
    void validateTicket_shouldScanTicketAndReturnResponse() {
        ticket.setScanned(false);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        event.setStatus(EventStatus.PUBLISHED);

        when(ticketRepository.findByQrCodeData("qrcode-123")).thenReturn(Optional.of(ticket));

        TicketResponse response = eventService.validateTicket("qrcode-123");

        assertThat(response).isNotNull();
        assertThat(ticket.isScanned()).isTrue();
        assertThat(ticket.getScannedAt()).isNotNull();
        verify(ticketRepository).save(ticket);
    }

    @Test
    void validateTicket_whenAlreadyScanned_shouldThrowException() {
        ticket.setScanned(true);
        ticket.setScannedAt(LocalDateTime.now());

        when(ticketRepository.findByQrCodeData("qrcode-123")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> eventService.validateTicket("qrcode-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ce billet a déjà été scanné");

        verify(ticketRepository, never()).save(ticket);
    }

    @Test
    void cancelAbandonedReservations_shouldCancelExpiredPendingReservations() {
        reservation.setStatus(ReservationStatus.PENDING);
        event.setReservedCount(1);
        event.setStatus(EventStatus.FULL);

        when(reservationRepository.findByStatusAndReservedAtBefore(eq(ReservationStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(reservation));

        eventService.cancelAbandonedReservations();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(event.getReservedCount()).isEqualTo(0);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);

        verify(reservationRepository).save(reservation);
        verify(eventRepository).save(event);
    }

    @Test
    void cancelEvent_whenAlreadyCancelled_shouldStillDeleteOrphanTickets() {
        event.setStatus(EventStatus.CANCELLED);
        reservation.setStatus(ReservationStatus.CANCELLED);

        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(artistRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(artist));
        when(reservationRepository.findByEventId(event.getId())).thenReturn(List.of(reservation));
        when(ticketRepository.findByReservationId(reservation.getId())).thenReturn(Optional.of(ticket));

        eventService.cancelEvent(event.getId(), "john.doe@example.com");

        verify(ticketRepository).delete(ticket);
        verify(notificationService, never()).createNotification(any(), anyString(), any());
        verify(eventRepository).save(event);
    }

    @Test
    void getMyReservations_shouldHideCancelledReservations() {
        Reservation cancelledReservation = Reservation.builder()
                .event(event)
                .userId(user.getId())
                .status(ReservationStatus.CANCELLED)
                .reservedAt(LocalDateTime.now())
                .build();
        cancelledReservation.setId(UUID.randomUUID());

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(reservationRepository.findByUserId(user.getId())).thenReturn(List.of(cancelledReservation, reservation));

        List<ReservationResponse> result = eventService.getMyReservations("alice@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void getLocations_shouldReturnLocations() {
        when(artistRepository.findByStatus("ACTIVE")).thenReturn(List.of(artist));
        when(eventRepository.findDistinctLocations()).thenReturn(List.of("Paris", "Lyon"));

        List<String> result = eventService.getLocations();

        assertThat(result).containsExactlyInAnyOrder("Paris", "Lyon");
    }
}
