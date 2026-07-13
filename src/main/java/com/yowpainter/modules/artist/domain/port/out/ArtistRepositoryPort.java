package com.yowpainter.modules.artist.domain.port.out;

import com.yowpainter.modules.artist.domain.model.Artist;
import java.util.UUID;

public interface ArtistRepositoryPort {

    java.util.Optional<Artist> findBySlug(String slug);
    java.util.Optional<Artist> findById(UUID id);
    java.util.Optional<Artist> findByEmail(String email);
    java.util.Optional<Artist> findByKernelUserId(UUID kernelUserId);
    java.util.List<Artist> findByStatus(String status);
    java.util.List<Artist> findByStatusIn(java.util.Collection<String> statuses);
    java.util.List<Artist> findAllWithValidatedOrganization();
    java.util.List<Artist> findAll();
    Artist save(Artist artist);
    long count();
}
