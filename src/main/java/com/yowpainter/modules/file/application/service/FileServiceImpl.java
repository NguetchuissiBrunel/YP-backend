package com.yowpainter.modules.file.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.model.UserRole;
import com.yowpainter.modules.file.infrastructure.adapter.in.web.dto.FileResponseDto;
import com.yowpainter.shared.kernel.port.KernelFilePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final KernelFilePort kernelFilePort;
    private final ArtistRepositoryPort artistRepository;

    @Override
    public FileResponseDto uploadFile(MultipartFile file, String documentType, String accessToken) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier requis");
        }

        UUID organizationId = null;
        org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AppUser appUser) {
            Object unproxied = org.hibernate.Hibernate.unproxy(appUser);
            if (unproxied instanceof Artist artist) {
                organizationId = artist.getOrganizationId();
            } else if (appUser.getRole() == UserRole.ROLE_ARTIST) {
                organizationId = artistRepository.findByEmail(appUser.getEmail())
                        .map(Artist::getOrganizationId)
                        .orElse(null);
            }
        }

        try {
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) {
                fileName = "uploaded-file";
            }
            KernelFilePort.FileView uploaded = kernelFilePort.upload(
                    new KernelFilePort.UploadFileCommand(
                            organizationId,
                            file.getBytes(),
                            fileName,
                            file.getContentType(),
                            documentType != null ? documentType : "GENERAL"
                    ),
                    accessToken
            );
            return FileResponseDto.builder()
                    .id(uploaded.id())
                    .fileName(uploaded.fileName())
                    .contentType(uploaded.contentType())
                    .downloadUrl("/api/files/" + uploaded.id())
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de lire le fichier", e);
        }
    }

    @Override
    public KernelFilePort.DownloadFileView downloadFile(UUID fileId, String accessToken) {
        if (fileId == null) {
            throw new IllegalArgumentException("L'identifiant du fichier (fileId) est requis");
        }
        return kernelFilePort.download(fileId, accessToken);
    }

    @Override
    public KernelFilePort.DownloadStreamView downloadFileStream(UUID fileId, org.springframework.http.HttpHeaders clientHeaders, String accessToken) {
        if (fileId == null) {
            throw new IllegalArgumentException("L'identifiant du fichier (fileId) est requis");
        }
        return kernelFilePort.downloadStream(fileId, clientHeaders, accessToken);
    }
}
