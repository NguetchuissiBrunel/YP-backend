package com.yowpainter.modules.artwork.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.artwork.domain.model.*;
import com.yowpainter.modules.artwork.domain.port.out.ArtworkCommentRepositoryPort;
import com.yowpainter.modules.artwork.domain.port.out.ArtworkLikeRepositoryPort;
import com.yowpainter.modules.artwork.domain.port.out.ArtworkRepositoryPort;
import com.yowpainter.modules.artwork.infrastructure.adapter.in.web.dto.*;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.notification.application.service.NotificationService;
import com.yowpainter.shared.kernel.port.KernelFilePort;
import com.yowpainter.modules.shop.domain.port.out.ProductRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.yowpainter.shared.tenant.TenantTransactionExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ArtworkServiceTest {

    @Mock private ArtworkRepositoryPort artworkRepository;
    @Mock private ArtistRepositoryPort artistRepository;
    @Mock private AppUserRepositoryPort userRepository;
    @Mock private ArtworkLikeRepositoryPort likeRepository;
    @Mock private ArtworkCommentRepositoryPort commentRepository;
    @Mock private ProductRepositoryPort productRepository;
    @Mock private NotificationService notificationService;
    @Mock private KernelFilePort kernelFilePort;
    @Mock private TenantTransactionExecutor tenantTransactionExecutor;

    @InjectMocks
    private ArtworkService artworkService;

    private Artist artist;
    private AppUser user;
    private Artwork artwork;

    @BeforeEach
    void setUp() {
        lenient().when(tenantTransactionExecutor.execute(any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(0);
                    return supplier.get();
                });
        lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(tenantTransactionExecutor).execute(any(Runnable.class));

        artist = Artist.builder()
                .firstName("Jean").lastName("Artiste")
                .email("jean@example.com")
                .artistName("Jean Studio")
                .slug("jean-studio")
                .organizationId(UUID.randomUUID())
                .build();
        artist.setId(UUID.randomUUID());

        user = AppUser.builder()
                .firstName("Marie").lastName("Dupont")
                .email("marie@example.com")
                .build();
        user.setId(UUID.randomUUID());

        artwork = Artwork.builder()
                .artistId(artist.getId())
                .title("La Belle Peinture")
                .description("Une oeuvre magnifique")
                .technique(ArtworkTechnique.OIL)
                .style(ArtworkStyle.FIGURATIVE)
                .dimensions("80x60 cm")
                .status(ArtworkStatus.DRAFT)
                .build();
        artwork.setId(UUID.randomUUID());
    }

    // ─── createArtwork ───────────────────────────────────────────────────────

    @Test
    void createArtwork_shouldSaveAndReturnResponse() {
        ArtworkCreateRequest request = ArtworkCreateRequest.builder()
                .title("La Belle Peinture").description("Une oeuvre magnifique")
                .technique(ArtworkTechnique.OIL).style(ArtworkStyle.FIGURATIVE)
                .dimensions("80x60 cm").build();

        when(artistRepository.findByEmail("jean@example.com")).thenReturn(Optional.of(artist));
        when(artworkRepository.save(any(Artwork.class))).thenReturn(artwork);
        when(artistRepository.findById(artist.getId())).thenReturn(Optional.of(artist));

        ArtworkResponse response = artworkService.createArtwork("jean@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("La Belle Peinture");
        assertThat(response.getStatus()).isEqualTo(ArtworkStatus.DRAFT);
        verify(artworkRepository).save(any(Artwork.class));
    }

    @Test
    void createArtwork_whenArtistNotFound_shouldThrowException() {
        when(artistRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        ArtworkCreateRequest request = ArtworkCreateRequest.builder().title("Test").build();

        assertThatThrownBy(() -> artworkService.createArtwork("unknown@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Artiste introuvable");
    }

    // ─── getPublicArtworksByArtistSlug ───────────────────────────────────────

    @Test
    void getPublicArtworksByArtistSlug_shouldReturnList() {
        when(artistRepository.findBySlug("jean-studio")).thenReturn(Optional.of(artist));
        when(artworkRepository.findPublicArtworksByArtistId(artist.getId())).thenReturn(List.of(artwork));
        when(artistRepository.findById(artist.getId())).thenReturn(Optional.of(artist));

        List<ArtworkResponse> result = artworkService.getPublicArtworksByArtistSlug("jean-studio");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("La Belle Peinture");
    }

    @Test
    void getPublicArtworksByArtistSlug_whenSlugNotFound_shouldThrowException() {
        when(artistRepository.findBySlug("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artworkService.getPublicArtworksByArtistSlug("inconnu"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Artiste non trouve");
    }

    // ─── getMyArtworks ───────────────────────────────────────────────────────

    @Test
    void getMyArtworks_shouldReturnArtistArtworks() {
        when(artistRepository.findByEmail("jean@example.com")).thenReturn(Optional.of(artist));
        when(artworkRepository.findByArtistId(artist.getId())).thenReturn(List.of(artwork));
        when(artistRepository.findById(artist.getId())).thenReturn(Optional.of(artist));

        List<ArtworkResponse> result = artworkService.getMyArtworks("jean@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getArtistName()).isEqualTo("Jean Studio");
    }

    // ─── getArtworkById ──────────────────────────────────────────────────────

    @Test
    void getArtworkById_shouldIncrementViewCountAndReturn() {
        artwork.setViewCount(5);
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));
        when(artworkRepository.save(any(Artwork.class))).thenReturn(artwork);
        when(artistRepository.findById(artist.getId())).thenReturn(Optional.of(artist));

        ArtworkResponse response = artworkService.getArtworkById(artwork.getId());

        assertThat(response).isNotNull();
        assertThat(artwork.getViewCount()).isEqualTo(6);
        verify(artworkRepository).save(artwork);
    }

    @Test
    void getArtworkById_whenNotFound_shouldThrowException() {
        UUID unknownId = UUID.randomUUID();
        when(artworkRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artworkService.getArtworkById(unknownId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Oeuvre non trouvee");
    }

    // ─── toggleLike ──────────────────────────────────────────────────────────

    @Test
    void toggleLike_whenNotLiked_shouldAddLikeAndNotify() {
        artwork.setLikeCount(0);
        when(artistRepository.findBySlug("jean-studio")).thenReturn(Optional.of(artist));
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));
        when(userRepository.findByEmail("marie@example.com")).thenReturn(Optional.of(user));
        when(likeRepository.findByArtworkIdAndUserId(artwork.getId(), user.getId())).thenReturn(Optional.empty());

        artworkService.toggleLike("jean-studio", artwork.getId(), "marie@example.com");

        assertThat(artwork.getLikeCount()).isEqualTo(1);
        verify(likeRepository).save(any(ArtworkLike.class));
        verify(notificationService).createNotification(eq(artwork.getArtistId()), anyString());
        verify(artworkRepository).save(artwork);
    }

    @Test
    void toggleLike_whenAlreadyLiked_shouldRemoveLike() {
        artwork.setLikeCount(3);
        ArtworkLike existingLike = ArtworkLike.builder().artwork(artwork).user(user).build();

        when(artistRepository.findBySlug("jean-studio")).thenReturn(Optional.of(artist));
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));
        when(userRepository.findByEmail("marie@example.com")).thenReturn(Optional.of(user));
        when(likeRepository.findByArtworkIdAndUserId(artwork.getId(), user.getId())).thenReturn(Optional.of(existingLike));

        artworkService.toggleLike("jean-studio", artwork.getId(), "marie@example.com");

        assertThat(artwork.getLikeCount()).isEqualTo(2);
        verify(likeRepository).delete(existingLike);
        verify(notificationService, never()).createNotification(any(), anyString());
    }

    @Test
    void toggleLike_likeCountShouldNotGoBelowZero() {
        artwork.setLikeCount(0);
        ArtworkLike existingLike = ArtworkLike.builder().artwork(artwork).user(user).build();

        when(artistRepository.findBySlug("jean-studio")).thenReturn(Optional.of(artist));
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));
        when(userRepository.findByEmail("marie@example.com")).thenReturn(Optional.of(user));
        when(likeRepository.findByArtworkIdAndUserId(artwork.getId(), user.getId())).thenReturn(Optional.of(existingLike));

        artworkService.toggleLike("jean-studio", artwork.getId(), "marie@example.com");

        assertThat(artwork.getLikeCount()).isEqualTo(0);
    }

    // ─── addComment ──────────────────────────────────────────────────────────

    @Test
    void addComment_shouldSaveCommentAndNotify() {
        CommentRequest request = new CommentRequest();
        request.setContent("Magnifique tableau !");
        ArtworkComment saved = ArtworkComment.builder().artwork(artwork).user(user).content("Magnifique tableau !").build();
        saved.setId(UUID.randomUUID());

        when(artistRepository.findBySlug("jean-studio")).thenReturn(Optional.of(artist));
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));
        when(userRepository.findByEmail("marie@example.com")).thenReturn(Optional.of(user));
        when(commentRepository.save(any(ArtworkComment.class))).thenReturn(saved);

        CommentResponse response = artworkService.addComment("jean-studio", artwork.getId(), "marie@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("Magnifique tableau !");
        verify(notificationService).createNotification(eq(artwork.getArtistId()), anyString());
    }

    // ─── updateArtwork ───────────────────────────────────────────────────────

    @Test
    void updateArtwork_shouldModifyAndSave() {
        ArtworkCreateRequest request = ArtworkCreateRequest.builder()
                .title("Nouveau Titre").description("Nouvelle description")
                .technique(ArtworkTechnique.WATERCOLOR).style(ArtworkStyle.IMPRESSIONISM)
                .dimensions("100x80 cm").build();

        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));
        when(artistRepository.findByEmail("jean@example.com")).thenReturn(Optional.of(artist));
        when(artworkRepository.save(any(Artwork.class))).thenReturn(artwork);
        when(artistRepository.findById(artist.getId())).thenReturn(Optional.of(artist));

        ArtworkResponse response = artworkService.updateArtwork(artwork.getId(), "jean@example.com", request);

        assertThat(response).isNotNull();
        assertThat(artwork.getTitle()).isEqualTo("Nouveau Titre");
        assertThat(artwork.getTechnique()).isEqualTo(ArtworkTechnique.WATERCOLOR);
        verify(artworkRepository).save(artwork);
    }

    @Test
    void updateArtwork_whenNotOwner_shouldThrowException() {
        Artist other = Artist.builder().email("other@example.com").build();
        other.setId(UUID.randomUUID());

        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));
        when(artistRepository.findByEmail("other@example.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> artworkService.updateArtwork(artwork.getId(), "other@example.com",
                ArtworkCreateRequest.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acces non autorise");
    }

    // ─── updateStatus ────────────────────────────────────────────────────────

    @Test
    void updateStatus_toPublished_shouldSetPublishedAt() {
        artwork.setPublishedAt(null);
        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));
        when(artistRepository.findByEmail("jean@example.com")).thenReturn(Optional.of(artist));
        when(productRepository.findByArtworkId(artwork.getId())).thenReturn(Optional.empty());

        artworkService.updateStatus(artwork.getId(), "jean@example.com", ArtworkStatus.PUBLISHED);

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.PUBLISHED);
        assertThat(artwork.getPublishedAt()).isNotNull();
        verify(artworkRepository).save(artwork);
    }

    @Test
    void updateStatus_toSuspended_shouldDeactivateLinkedProduct() {
        com.yowpainter.modules.shop.domain.model.Product product =
                com.yowpainter.modules.shop.domain.model.Product.builder()
                        .artistId(artist.getId()).name("Produit Test").isActive(true).build();
        product.setId(UUID.randomUUID());

        when(artworkRepository.findById(artwork.getId())).thenReturn(Optional.of(artwork));
        when(artistRepository.findByEmail("jean@example.com")).thenReturn(Optional.of(artist));
        when(productRepository.findByArtworkId(artwork.getId())).thenReturn(Optional.of(product));

        artworkService.updateStatus(artwork.getId(), "jean@example.com", ArtworkStatus.SUSPENDED);

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.SUSPENDED);
        assertThat(product.isActive()).isFalse();
        verify(productRepository).save(product);
    }

    // ─── bulkDeleteArtworks ──────────────────────────────────────────────────

    @Test
    void bulkDeleteArtworks_shouldDeleteOwnerArtworks() {
        when(artistRepository.findByEmail("jean@example.com")).thenReturn(Optional.of(artist));
        when(artworkRepository.findAllById(List.of(artwork.getId()))).thenReturn(List.of(artwork));

        artworkService.bulkDeleteArtworks(List.of(artwork.getId()), "jean@example.com");

        verify(artworkRepository).deleteAll(List.of(artwork));
    }

    @Test
    void bulkDeleteArtworks_whenNotOwner_shouldThrowException() {
        Artist other = Artist.builder().email("other@example.com").build();
        other.setId(UUID.randomUUID());

        when(artistRepository.findByEmail("other@example.com")).thenReturn(Optional.of(other));
        when(artworkRepository.findAllById(List.of(artwork.getId()))).thenReturn(List.of(artwork));

        assertThatThrownBy(() -> artworkService.bulkDeleteArtworks(List.of(artwork.getId()), "other@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acces non autorise");
    }

    // ─── getSuggestions ──────────────────────────────────────────────────────

    @Test
    void getSuggestions_shouldFilterTagsByQuery() {
        when(artworkRepository.findDistinctTags())
                .thenReturn(List.of("Paysage", "Portrait", "Abstrait", "Pavage"));

        List<String> suggestions = artworkService.getSuggestions("pa");

        assertThat(suggestions).containsExactlyInAnyOrder("Paysage", "Pavage");
    }

    @Test
    void getSuggestions_withNoMatch_shouldReturnEmptyList() {
        when(artworkRepository.findDistinctTags()).thenReturn(List.of("Paysage", "Portrait"));

        List<String> result = artworkService.getSuggestions("zzz");

        assertThat(result).isEmpty();
    }
}
