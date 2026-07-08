package com.yowpainter.modules.event.infrastructure.adapter.in.web.dto;

import com.yowpainter.modules.event.domain.model.EventStatus;
import com.yowpainter.modules.event.domain.model.EventType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class EventResponse {
    private UUID id;
    private UUID artistId;
    private String name;
    private String description;
    private String posterUrl;
    private Instant startDateTime;
    private Instant endDateTime;
    private String location;
    private EventType type;
    private int maxCapacity;
    private int reservedCount;
    private BigDecimal ticketPrice;
    private EventStatus status;
}
