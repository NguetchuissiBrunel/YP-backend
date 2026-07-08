package com.yowpainter.modules.event.infrastructure.adapter.in.web.dto;

import com.yowpainter.modules.event.domain.model.EventType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventCreateRequest {

    @NotBlank(message = "Le nom de l'evenement est requis")
    private String name;

    private String description;

    private String posterUrl;

    @NotNull(message = "La date de debut est requise")
    @Future(message = "La date de debut doit etre dans le futur")
    private Instant startDateTime;

    @NotNull(message = "La date de fin est requise")
    @Future(message = "La date de fin doit etre dans le futur")
    private Instant endDateTime;

    private String location;

    @NotNull(message = "Le type d'evenement est requis")
    private EventType type;

    @Builder.Default
    private int maxCapacity = 0; // 0 = illimité
    
    @Builder.Default
    private BigDecimal ticketPrice = BigDecimal.ZERO; // Gratuit par defaut
}
