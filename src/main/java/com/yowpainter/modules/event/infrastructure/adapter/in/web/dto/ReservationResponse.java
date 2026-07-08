package com.yowpainter.modules.event.infrastructure.adapter.in.web.dto;

import com.yowpainter.modules.event.domain.model.ReservationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ReservationResponse {
    private UUID id;
    private UUID eventId;
    private String eventName;
    private UUID userId;
    private String userName;
    private String userEmail;
    private ReservationStatus status;
    private Instant createdAt;
    private String qrCodeData;
}
