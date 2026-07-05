package com.yowpainter.modules.shop.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.shop.domain.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<Product, java.util.UUID> {

    java.util.Optional<Product> findByArtwork_Id(UUID artworkId);
    java.util.List<Product> findByArtistId(UUID artistId);
    java.util.List<Product> findByArtistIdAndIsActiveTrue(UUID artistId);
    java.util.List<Product> findByIsActiveTrue();
}
