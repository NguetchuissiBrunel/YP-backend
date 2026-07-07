package com.yowpainter.modules.shop.application.service;

import com.yowpainter.config.KernelProperties;
import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.artwork.domain.model.Artwork;
import com.yowpainter.modules.artwork.domain.port.out.ArtworkRepositoryPort;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.shop.domain.model.Order;
import com.yowpainter.modules.shop.domain.model.OrderItem;
import com.yowpainter.modules.shop.domain.model.OrderStatus;
import com.yowpainter.modules.shop.domain.model.Product;
import com.yowpainter.modules.shop.domain.port.out.OrderRepositoryPort;
import com.yowpainter.modules.shop.domain.port.out.ProductRepositoryPort;
import com.yowpainter.modules.shop.infrastructure.adapter.in.web.dto.OrderCreateRequest;
import com.yowpainter.modules.shop.infrastructure.adapter.in.web.dto.OrderItemResponse;
import com.yowpainter.modules.shop.infrastructure.adapter.in.web.dto.OrderResponse;
import com.yowpainter.modules.shop.infrastructure.adapter.in.web.dto.ProductCreateRequest;
import com.yowpainter.modules.shop.infrastructure.adapter.in.web.dto.ProductResponse;
import com.yowpainter.shared.context.OrganizationContext;
import com.yowpainter.shared.context.RequestContext;
import com.yowpainter.shared.kernel.port.KernelProductPort;
import com.yowpainter.shared.kernel.port.KernelSalesPort;
import com.yowpainter.shared.tenant.TenantTransactionExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KernelCommerceService {

    private final KernelProductPort kernelProductPort;
    private final KernelSalesPort kernelSalesPort;
    private final ArtistRepositoryPort artistRepository;
    private final ArtworkRepositoryPort artworkRepository;
    private final ProductRepositoryPort productRepository;
    private final OrderRepositoryPort orderRepository;
    private final AppUserRepositoryPort appUserRepository;
    private final KernelProperties kernelProperties;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    @Transactional
    public ProductResponse createProduct(String artistEmail, ProductCreateRequest request) {
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste introuvable"));
        if (artist.getOrganizationId() == null) {
            throw new IllegalStateException("Organisation kernel manquante pour cet artiste");
        }

        String accessToken = requireAccessToken();
        Artwork artwork = null;
        if (request.getArtworkId() != null) {
            artwork = artworkRepository.findById(request.getArtworkId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Oeuvre introuvable pour artworkId=" + request.getArtworkId()
                                    + ". Creez d'abord une oeuvre via POST /api/artworks ou retirez artworkId."
                    ));
            if (!artwork.getArtistId().equals(artist.getId())) {
                throw new IllegalArgumentException("Cette oeuvre n'appartient pas a votre profil artiste");
            }
            artwork.setStatus(com.yowpainter.modules.artwork.domain.model.ArtworkStatus.ON_SALE);
            artwork.setOrganizationId(artist.getOrganizationId());
            artworkRepository.save(artwork);
        }

        String sku = "ART-" + (artwork != null ? artwork.getId() : UUID.randomUUID()).toString().substring(0, 8);
        KernelProductPort.ProductView kernelProduct = kernelProductPort.createProduct(
                new KernelProductPort.CreateProductCommand(
                        artist.getOrganizationId(),
                        sku,
                        request.getName(),
                        request.getDescription(),
                        request.getPrice(),
                        kernelProperties.defaultCurrency()
                ),
                accessToken
        );

        Product localProduct = Product.builder()
                .artistId(artist.getId())
                .artwork(artwork)
                .name(kernelProduct.name())
                .description(kernelProduct.description())
                .price(kernelProduct.unitPrice())
                .stockQuantity(request.getStockQuantity() > 0 ? request.getStockQuantity() : 1)
                .isActive(true)
                .kernelProductId(kernelProduct.id())
                .organizationId(artist.getOrganizationId())
                .build();

        return mapToProductResponse(productRepository.save(localProduct));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByArtistSlug(String slug) {
        Artist artist = artistRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));
        if (artist.getOrganizationId() == null) {
            return List.of();
        }
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            return tenantTransactionExecutor.execute(() ->
                productRepository.findByArtistIdAndIsActiveTrue(artist.getId()).stream()
                        .map(this::mapToProductResponse)
                        .collect(Collectors.toList())
            );
        } finally {
            OrganizationContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllPublicProducts() {
        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        List<ProductResponse> allProducts = new java.util.ArrayList<>();
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                List<ProductResponse> tenantProducts = tenantTransactionExecutor.execute(() -> 
                    productRepository.findByIsActiveTrue().stream()
                            .filter(product -> product.getKernelProductId() != null && artist.getId().equals(product.getArtistId()))
                            .map(this::mapToProductResponse)
                            .collect(Collectors.toList())
                );
                allProducts.addAll(tenantProducts);
            } catch (Exception e) {
                // Ignore schema issues and keep searching
            } finally {
                OrganizationContext.clear();
            }
        }
        return allProducts;
    }

    public OrderResponse placeOrder(String buyerEmail, String artistSlug, OrderCreateRequest request) {
        Artist artist = artistRepository.findBySlug(artistSlug)
                .orElseThrow(() -> new IllegalArgumentException("Artiste introuvable"));
        if (artist.getOrganizationId() == null) {
            throw new IllegalStateException("Organisation de l'artiste manquante");
        }
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            return tenantTransactionExecutor.execute(() -> {
                AppUser buyer = appUserRepository.findByEmail(buyerEmail)
                        .orElseThrow(() -> new IllegalArgumentException("Acheteur introuvable"));
                Product product = productRepository.findById(request.getProductId())
                        .orElseThrow(() -> new IllegalArgumentException("Produit introuvable"));
                if (product.getKernelProductId() == null || product.getOrganizationId() == null) {
                    throw new IllegalStateException("Produit non synchronise avec le kernel");
                }

                String accessToken = requireAccessToken();
                KernelSalesPort.SalesOrderView kernelOrder = kernelSalesPort.createOrder(
                        new KernelSalesPort.CreateSalesOrderCommand(
                                product.getOrganizationId(),
                                product.getKernelProductId(),
                                BigDecimal.valueOf(request.getQuantity()),
                                product.getPrice(),
                                kernelProperties.defaultCurrency(),
                                "YP-" + UUID.randomUUID().toString().substring(0, 8)
                        ),
                        accessToken
                );

                if (product.getStockQuantity() <= request.getQuantity() && product.getArtwork() != null) {
                    Artwork artwork = product.getArtwork();
                    artwork.setStatus(com.yowpainter.modules.artwork.domain.model.ArtworkStatus.SOLD);
                    artworkRepository.save(artwork);
                }

                Order order = Order.builder()
                        .buyerId(buyer.getId())
                        .shippingAddress(request.getShippingAddress())
                        .status(mapKernelStatus(kernelOrder.status()))
                        .totalAmount(kernelOrder.totalAmount() != null ? kernelOrder.totalAmount() : product.getPrice())
                        .kernelSalesOrderId(kernelOrder.id())
                        .organizationId(product.getOrganizationId())
                        .build();
                order.addItem(OrderItem.builder()
                        .product(product)
                        .quantity(request.getQuantity())
                        .unitPrice(product.getPrice())
                        .build());

                return mapToOrderResponse(orderRepository.save(order));
            });
        } finally {
            OrganizationContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMySales(String artistEmail) {
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste introuvable"));
        return orderRepository.findByOrganizationIdOrderByCreatedAtDesc(artist.getOrganizationId()).stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        if (OrganizationContext.getOrganizationId() != null) {
            return mapToOrderResponse(orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Commande non trouvée")));
        }

        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                java.util.Optional<OrderResponse> orderResOpt = tenantTransactionExecutor.execute(() -> {
                    java.util.Optional<Order> orderOpt = orderRepository.findById(orderId);
                    if (orderOpt.isPresent() && artist.getOrganizationId().equals(orderOpt.get().getOrganizationId())) {
                        return java.util.Optional.of(mapToOrderResponse(orderOpt.get()));
                    }
                    return java.util.Optional.empty();
                });
                if (orderResOpt.isPresent()) {
                    return orderResOpt.get();
                }
            } catch (Exception e) {
                // Ignore schema issues and keep searching
            } finally {
                OrganizationContext.clear();
            }
        }
        throw new IllegalArgumentException("Commande non trouvée");
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getInventory(String artistEmail) {
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste introuvable"));
        return productRepository.findByArtistId(artist.getId()).stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    private String requireAccessToken() {
        String token = RequestContext.accessToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Token utilisateur requis pour les operations commerce kernel");
        }
        return token;
    }

    private OrderStatus mapKernelStatus(String kernelStatus) {
        if (kernelStatus == null) {
            return OrderStatus.PENDING_PAYMENT;
        }
        return switch (kernelStatus.toUpperCase()) {
            case "CONFIRMED", "COMPLETED" -> OrderStatus.PAID;
            case "CANCELLED" -> OrderStatus.CANCELLED;
            default -> OrderStatus.PENDING_PAYMENT;
        };
    }

    private OrderResponse mapToOrderResponse(Order order) {
        AppUser buyer = appUserRepository.findById(order.getBuyerId()).orElse(null);
        String buyerName = buyer != null ? buyer.getFirstName() + " " + buyer.getLastName() : "Inconnu";
        return OrderResponse.builder()
                .id(order.getId())
                .buyerId(order.getBuyerId())
                .buyerName(buyerName)
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .createdAt(order.getCreatedAt())
                .items(order.getItems().stream().map(i -> OrderItemResponse.builder()
                        .productId(i.getProduct().getId())
                        .productName(i.getProduct().getName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .build()).collect(Collectors.toList()))
                .build();
    }

    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .artistId(product.getArtistId())
                .artworkId(product.getArtwork() != null ? product.getArtwork().getId() : null)
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .isActive(product.isActive())
                .build();
    }
}
