package com.yowpainter.modules.event.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.event.domain.model.Event;
import com.yowpainter.modules.event.domain.model.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface EventJpaRepository extends JpaRepository<Event, UUID> {

    List<Event> findByArtistId(UUID artistId);

    @Query("SELECT e FROM Event e WHERE (e.status = com.yowpainter.modules.event.domain.model.EventStatus.PUBLISHED OR e.status = com.yowpainter.modules.event.domain.model.EventStatus.ONGOING) AND (e.endDateTime > :now OR (e.endDateTime IS NULL AND e.startDateTime > :now)) ORDER BY e.startDateTime ASC")
    List<Event> findUpcomingEvents(@Param("now") LocalDateTime now);

    @Query("SELECT e FROM Event e WHERE (e.status = com.yowpainter.modules.event.domain.model.EventStatus.PUBLISHED OR e.status = com.yowpainter.modules.event.domain.model.EventStatus.ONGOING) AND (LOWER(e.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(e.description) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(e.location) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Event> searchPublicEvents(@Param("query") String query);

    @Query("SELECT DISTINCT e.location FROM Event e WHERE e.location IS NOT NULL AND e.location <> ''")
    List<String> findDistinctLocations();

    long countByStatusAndStartDateTimeAfter(EventStatus status, LocalDateTime startDateTime);
}
