package com.yowpainter.modules.artist.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.artist.domain.model.Artist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistJpaRepository extends JpaRepository<Artist, java.util.UUID> {

    java.util.Optional<Artist> findBySlug(String slug);
    java.util.Optional<Artist> findByEmail(String email);
    java.util.Optional<Artist> findByKernelUserId(java.util.UUID kernelUserId);
    java.util.List<Artist> findByStatus(String status);
}
