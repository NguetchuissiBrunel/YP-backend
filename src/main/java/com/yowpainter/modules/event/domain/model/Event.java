package com.yowpainter.modules.event.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "artist_id", nullable = false)
    private UUID artistId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "poster_url")
    private String posterUrl;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    private String location;

    /** Latitude WGS84 du lieu (nullable : localisation optionnelle). */
    @Column(name = "latitude")
    private Double latitude;

    /** Longitude WGS84 du lieu (nullable). */
    @Column(name = "longitude")
    private Double longitude;

    @Enumerated(EnumType.STRING)
    private EventType type;

    @Column(name = "max_capacity", nullable = false)
    @Builder.Default
    private int maxCapacity = 0; // 0 = illimité si l'artiste le conçoit comme public ouvert

    @Column(name = "reserved_count", nullable = false)
    @Builder.Default
    private int reservedCount = 0;

    @Column(name = "ticket_price", nullable = false)
    @Builder.Default
    private BigDecimal ticketPrice = BigDecimal.ZERO; // 0.00 = Gratuit

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    public boolean hasAvailableSeats() {
        return maxCapacity == 0 || reservedCount < maxCapacity;
    }
}
