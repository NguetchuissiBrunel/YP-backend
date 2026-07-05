package com.yowpainter.modules.shop.domain.port.out;

import com.yowpainter.modules.shop.domain.model.Product;
import java.util.UUID;

public interface ProductRepositoryPort {

    Product save(Product product);
    java.util.Optional<Product> findById(UUID id);
    java.util.Optional<Product> findByArtworkId(UUID artworkId);
    java.util.List<Product> findAll();
    java.util.List<Product> findByArtistId(UUID artistId);
    java.util.List<Product> findByArtistIdAndIsActiveTrue(UUID artistId);
    java.util.List<Product> findByIsActiveTrue();
}
