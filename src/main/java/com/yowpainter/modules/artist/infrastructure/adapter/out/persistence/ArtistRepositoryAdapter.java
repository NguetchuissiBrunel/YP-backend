package com.yowpainter.modules.artist.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ArtistRepositoryAdapter implements ArtistRepositoryPort {

    private final ArtistJpaRepository jpaRepository;

    @Override
    public java.util.Optional<Artist> findBySlug(String slug) {
        return jpaRepository.findBySlug(slug);
    }

    @Override
    public java.util.Optional<Artist> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public java.util.Optional<Artist> findByEmail(String email) {
        return jpaRepository.findByEmail(email);
    }

    @Override
    public java.util.Optional<Artist> findByKernelUserId(UUID kernelUserId) {
        return jpaRepository.findByKernelUserId(kernelUserId);
    }

    @Override
    public java.util.List<Artist> findByStatus(String status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public java.util.List<Artist> findByStatusIn(java.util.Collection<String> statuses) {
        return jpaRepository.findByStatusIn(statuses);
    }

    @Override
    public java.util.List<Artist> findAllWithValidatedOrganization() {
        return jpaRepository.findAllWithValidatedOrganization();
    }

    @Override
    public java.util.List<Artist> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public Artist save(Artist artist) {
        return jpaRepository.save(artist);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }
}
