package com.yowpainter.modules.admin.application.service;

import com.yowpainter.modules.admin.infrastructure.adapter.in.web.dto.PendingArtistResponse;
import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.model.ArtistRegistrationStatus;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtistApprovalServiceTest {

    @Mock
    private ArtistRepositoryPort artistRepository;

    @InjectMocks
    private ArtistApprovalService artistApprovalService;

    @Test
    void listPendingArtists_shouldIncludeOrganizationValidationRequiredStatus() {
        Artist pending = Artist.builder()
                .email("chedjoujohan12@gmail.com")
                .artistName("johan")
                .slug("johan")
                .status(ArtistRegistrationStatus.PENDING_APPROVAL)
                .build();
        pending.setId(UUID.randomUUID());
        pending.setCreatedAt(LocalDateTime.now());

        when(artistRepository.findByStatusIn(any())).thenReturn(List.of(pending));

        List<PendingArtistResponse> result = artistApprovalService.listPendingArtists();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("chedjoujohan12@gmail.com");
        assertThat(result.get(0).getStatus()).isEqualTo(ArtistRegistrationStatus.PENDING_APPROVAL);
    }
}
