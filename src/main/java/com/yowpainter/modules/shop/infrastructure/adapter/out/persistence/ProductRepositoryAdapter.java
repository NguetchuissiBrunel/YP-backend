package com.yowpainter.modules.shop.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.shop.domain.model.Product;
import com.yowpainter.modules.shop.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository jpaRepository;

    @Override
    public Product save(Product product) {
        return jpaRepository.save(product);
    }

    @Override
    public java.util.Optional<Product> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public java.util.Optional<Product> findByArtworkId(UUID artworkId) {
        return jpaRepository.findByArtwork_Id(artworkId);
    }

    @Override
    public java.util.List<Product> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public java.util.List<Product> findByArtistId(UUID artistId) {
        return jpaRepository.findByArtistId(artistId);
    }

    @Override
    public java.util.List<Product> findByArtistIdAndIsActiveTrue(UUID artistId) {
        return jpaRepository.findByArtistIdAndIsActiveTrue(artistId);
    }

    @Override
    public java.util.List<Product> findByIsActiveTrue() {
        return jpaRepository.findByIsActiveTrue();
    }
}
