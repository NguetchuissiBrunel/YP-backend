package com.yowpainter.modules.shop.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.artwork.domain.model.Artwork;
import com.yowpainter.modules.artwork.domain.model.ArtworkStatus;
import com.yowpainter.modules.artwork.domain.port.out.ArtworkRepositoryPort;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.shop.domain.model.*;
import com.yowpainter.modules.shop.domain.port.out.OrderRepositoryPort;
import com.yowpainter.modules.shop.domain.port.out.PaymentRepositoryPort;
import com.yowpainter.modules.shop.domain.port.out.ProductRepositoryPort;
import com.yowpainter.modules.shop.infrastructure.adapter.in.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShopServiceTest {

    @Mock private ProductRepositoryPort productRepository;
    @Mock private OrderRepositoryPort orderRepository;
    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private ArtworkRepositoryPort artworkRepository;
    @Mock private ArtistRepositoryPort artistRepository;
    @Mock private AppUserRepositoryPort appUserRepository;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks
    private ShopService shopService;

    private Artist artist;
    private AppUser buyer;
    private Product product;
    private Artwork artwork;
    private Order order;

    @BeforeEach
    void setUp() {
        artist = Artist.builder()
                .firstName("Jean").lastName("Artiste")
                .email("jean@example.com").slug("jean-studio")
                .build();
        artist.setId(UUID.randomUUID());

        buyer = AppUser.builder()
                .firstName("Marie").lastName("Dupont")
                .email("marie@example.com")
                .build();
        buyer.setId(UUID.randomUUID());

        artwork = Artwork.builder()
                .artistId(artist.getId())
                .title("Toile Unique")
                .status(ArtworkStatus.ON_SALE)
                .build();
        artwork.setId(UUID.randomUUID());

        product = Product.builder()
                .artistId(artist.getId())
                .artwork(artwork)
                .name("Toile Unique")
                .description("Une toile originale")
                .price(new BigDecimal("50000"))
                .stockQuantity(1)
                .isActive(true)
                .build();
        product.setId(UUID.randomUUID());

        order = Order.builder()
                .buyerId(buyer.getId())
                .status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(new BigDecimal("50000"))
                .shippingAddress("Yaoundé, Cameroun")
                .build();
        order.setId(UUID.randomUUID());
        OrderItem item = OrderItem.builder()
                .product(product).quantity(1).unitPrice(product.getPrice()).build();
        order.addItem(item);
    }

    // ─── createProduct ───────────────────────────────────────────────────────

    @Test
    void createProduct_withoutArtwork_shouldSaveAndReturnResponse() {
        ProductCreateRequest request = ProductCreateRequest.builder()
                .name("Affiche Print")
                .description("Reproduction numérique")
                .price(new BigDecimal("5000"))
                .stockQuantity(100)
                .build();

        Product savedProduct = Product.builder()
                .artistId(artist.getId()).name("Affiche Print")
                .price(new BigDecimal("5000")).stockQuantity(100).isActive(true).build();
        savedProduct.setId(UUID.randomUUID());

        when(artistRepository.findByEmail("jean@example.com")).thenReturn(Optional.of(artist));
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

        ProductResponse response = shopService.createProduct("jean@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Affiche Print");
        assertThat(response.getPrice()).isEqualTo(new BigDecimal("5000"));
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProduct_withArtwork_shouldSetArtworkOnSale() {
        artwork.setStatus(ArtworkStatus.PUBLISHED);
        ProductCreateRequest request = ProductCreateRequest.builder()
                .name("Toile Unique").artworkId(artwork.getId())
                .price(new BigDecimal("50000")).stockQuantity(1).build();

        when(artistRepository.findByEmail("jean@example.com")).thenReturn(Optional.of(artist));
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        shopService.createProduct("jean@example.com", request);

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.ON_SALE);
        verify(artworkRepository).save(artwork);
    }

    @Test
    void createProduct_withArtworkNotOwnedByArtist_shouldThrowException() {
        Artist otherArtist = Artist.builder().email("other@example.com").build();
        otherArtist.setId(UUID.randomUUID());
        // artwork belongs to `artist`, not `otherArtist`

        ProductCreateRequest request = ProductCreateRequest.builder()
                .name("Test").artworkId(artwork.getId()).price(BigDecimal.TEN).stockQuantity(1).build();

        when(artistRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherArtist));
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));

        assertThatThrownBy(() -> shopService.createProduct("other@example.com", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not authorized");
    }

    // ─── getProductsByArtistSlug ─────────────────────────────────────────────

    @Test
    void getProductsByArtistSlug_shouldReturnActiveProducts() {
        when(artistRepository.findBySlug("jean-studio")).thenReturn(Optional.of(artist));
        when(productRepository.findByArtistIdAndIsActiveTrue(artist.getId())).thenReturn(List.of(product));

        List<ProductResponse> result = shopService.getProductsByArtistSlug("jean-studio");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Toile Unique");
    }

    @Test
    void getProductsByArtistSlug_whenSlugNotFound_shouldThrowException() {
        when(artistRepository.findBySlug("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.getProductsByArtistSlug("inconnu"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Artiste non trouve");
    }

    // ─── placeOrder ──────────────────────────────────────────────────────────

    @Test
    void placeOrder_shouldDecrementStockAndCreateOrder() {
        product.setStockQuantity(5);
        OrderCreateRequest request = OrderCreateRequest.builder()
                .productId(product.getId()).quantity(2)
                .shippingAddress("Douala, Cameroun").build();

        when(appUserRepository.findByEmail("marie@example.com")).thenReturn(Optional.of(buyer));
        when(productRepository.findByIdWithPessimisticWriteLock(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
        when(appUserRepository.findById(buyer.getId())).thenReturn(Optional.of(buyer));

        OrderResponse response = shopService.placeOrder("marie@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(product.getStockQuantity()).isEqualTo(3); // 5 - 2
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void placeOrder_whenStockInsufficient_shouldThrowException() {
        product.setStockQuantity(1);
        OrderCreateRequest request = OrderCreateRequest.builder()
                .productId(product.getId()).quantity(5)
                .shippingAddress("Douala").build();

        when(appUserRepository.findByEmail("marie@example.com")).thenReturn(Optional.of(buyer));
        when(productRepository.findByIdWithPessimisticWriteLock(product.getId())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> shopService.placeOrder("marie@example.com", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Produit epuise ou quantite insuffisante");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_whenProductInactive_shouldThrowException() {
        product.setActive(false);
        OrderCreateRequest request = OrderCreateRequest.builder()
                .productId(product.getId()).quantity(1).shippingAddress("Yaoundé").build();

        when(appUserRepository.findByEmail("marie@example.com")).thenReturn(Optional.of(buyer));
        when(productRepository.findByIdWithPessimisticWriteLock(product.getId())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> shopService.placeOrder("marie@example.com", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Produit epuise ou quantite insuffisante");
    }

    @Test
    void placeOrder_whenLastUnitSold_shouldMarkArtworkAsSold() {
        product.setStockQuantity(1);
        OrderCreateRequest request = OrderCreateRequest.builder()
                .productId(product.getId()).quantity(1)
                .shippingAddress("Yaoundé").build();

        when(appUserRepository.findByEmail("marie@example.com")).thenReturn(Optional.of(buyer));
        when(productRepository.findByIdWithPessimisticWriteLock(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
        when(appUserRepository.findById(buyer.getId())).thenReturn(Optional.of(buyer));

        shopService.placeOrder("marie@example.com", request);

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.SOLD);
        verify(artworkRepository).save(artwork);
    }

    // ─── updateOrderStatus ───────────────────────────────────────────────────

    @Test
    void updateOrderStatus_shouldChangeStatusAndSave() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        shopService.updateOrderStatus(order.getId(), OrderStatus.PAID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).save(order);
    }

    // ─── getOrderById ────────────────────────────────────────────────────────

    @Test
    void getOrderById_shouldReturnResponse() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(appUserRepository.findById(buyer.getId())).thenReturn(Optional.of(buyer));

        OrderResponse response = shopService.getOrderById(order.getId());

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(order.getId());
        assertThat(response.getBuyerName()).isEqualTo("Marie Dupont");
    }

    @Test
    void getOrderById_whenNotFound_shouldThrowException() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.getOrderById(unknownId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Commande non trouvée");
    }

    // ─── getMyPurchases ──────────────────────────────────────────────────────

    @Test
    void getMyPurchases_shouldReturnBuyerOrders() {
        when(appUserRepository.findByEmail("marie@example.com")).thenReturn(Optional.of(buyer));
        when(orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.getId())).thenReturn(List.of(order));
        when(appUserRepository.findById(buyer.getId())).thenReturn(Optional.of(buyer));

        List<OrderResponse> result = shopService.getMyPurchases("marie@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    // ─── getInventory ────────────────────────────────────────────────────────

    @Test
    void getInventory_shouldReturnAllProductsForArtist() {
        when(artistRepository.findByEmail("jean@example.com")).thenReturn(Optional.of(artist));
        when(productRepository.findByArtistId(artist.getId())).thenReturn(List.of(product));

        List<ProductResponse> result = shopService.getInventory("jean@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Toile Unique");
    }

    // ─── cancelAbandonedOrdersForTenant ─────────────────────────────────────

    @Test
    void cancelAbandonedOrdersForTenant_shouldCancelExpiredOrders() {
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        product.setStockQuantity(0);
        artwork.setStatus(ArtworkStatus.SOLD);

        when(orderRepository.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of(order));

        shopService.cancelAbandonedOrdersForTenant("jean-studio");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(1); // restocked
        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.ON_SALE); // restored
        verify(orderRepository).save(order);
        verify(productRepository).save(product);
        verify(artworkRepository).save(artwork);
    }

    @Test
    void cancelAbandonedOrdersForTenant_whenNoExpiredOrders_shouldDoNothing() {
        when(orderRepository.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of());

        shopService.cancelAbandonedOrdersForTenant("jean-studio");

        verify(orderRepository, never()).save(any());
    }
}
