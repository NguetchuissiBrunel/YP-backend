package com.yowpainter.shared.kernel.adapter;

import com.yowpainter.config.KernelProperties;
import com.yowpainter.shared.kernel.KernelHttpClient;
import com.yowpainter.shared.kernel.KernelSystemUserTokenProvider;
import com.yowpainter.shared.kernel.adapter.dto.KernelSignedUrlResponseDto;
import com.yowpainter.shared.kernel.adapter.dto.KernelStoredFileResponseDto;
import com.yowpainter.shared.kernel.port.KernelFilePort;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class KernelFileHttpAdapter implements KernelFilePort {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KernelFileHttpAdapter.class);

    /** Fichiers faits pour etre vus par tout visiteur : envoyes PUBLIC au noyau. */
    private static final java.util.Set<String> PUBLIC_DOCUMENT_TYPES =
            java.util.Set.of("ARTWORK_IMAGE", "ARTWORK_VIDEO", "PROFILE_PICTURE");

    private final KernelHttpClient kernelHttpClient;
    private final KernelProperties properties;
    private final KernelSystemUserTokenProvider kernelSystemUserTokenProvider;

    public KernelFileHttpAdapter(
            KernelHttpClient kernelHttpClient,
            KernelProperties properties,
            KernelSystemUserTokenProvider kernelSystemUserTokenProvider
    ) {
        this.kernelHttpClient = kernelHttpClient;
        this.properties = properties;
        this.kernelSystemUserTokenProvider = kernelSystemUserTokenProvider;
    }

    @Override
    public FileView upload(UploadFileCommand command, String accessToken) {
        boolean publicFile = PUBLIC_DOCUMENT_TYPES.contains(command.documentType());
        KernelStoredFileResponseDto response = kernelHttpClient.uploadMultipart(
                "/api/files",
                command.content(),
                command.fileName(),
                command.contentType(),
                command.documentType(),
                publicFile,
                KernelStoredFileResponseDto.class,
                command.organizationId(),
                accessToken
        );
        String baseUrl = properties.baseUrl() == null ? "" : properties.baseUrl().replaceAll("/$", "");
        String downloadUrl = baseUrl + "/api/files/" + response.id();
        if (publicFile) {
            downloadUrl = publicUrlOf(response.id(), command.organizationId(), accessToken, downloadUrl);
        }
        return new FileView(
                response.id(),
                response.fileName(),
                response.contentType(),
                downloadUrl
        );
    }

    /**
     * Adresse publique PERMANENTE du fichier, servie par le noyau sans session ni compte
     * ({@code /api/public/files/{tenant}/{id}}). Les oeuvres et les avatars sont faits pour etre vus
     * par des visiteurs : les servir via ce backend obligeait a s'authentifier aupres du noyau au nom
     * du visiteur, avec un compte systeme qui ne voit de toute facon pas les fichiers des autres
     * espaces. En cas d'echec, l'ancienne adresse est conservee : l'envoi ne doit pas echouer pour ca.
     */
    private String publicUrlOf(UUID fileId, UUID organizationId, String accessToken, String fallback) {
        try {
            KernelSignedUrlResponseDto signed = kernelHttpClient.get(
                    "/api/files/" + fileId + "/signed-url",
                    KernelSignedUrlResponseDto.class,
                    organizationId,
                    accessToken
            );
            if (signed != null && signed.url() != null && !signed.url().isBlank()) {
                String url = signed.url();
                int query = url.indexOf('?');
                return query >= 0 ? url.substring(0, query) : url;
            }
        } catch (RuntimeException ex) {
            log.warn("[files] Adresse publique indisponible pour {} : {}", fileId, ex.getMessage());
        }
        return fallback;
    }

    @Override
    public DownloadFileView download(UUID fileId, String accessToken) {
        String effectiveToken = accessToken;
        if (effectiveToken == null || effectiveToken.isBlank()) {
            effectiveToken = kernelSystemUserTokenProvider.getSystemUserAccessToken();
        }

        String path = "/api/files/" + fileId;
        org.springframework.http.ResponseEntity<byte[]> response = kernelHttpClient.download(
                path,
                null,
                effectiveToken
        );
        return new DownloadFileView(
                response.getBody(),
                response.getHeaders().getFirst(org.springframework.http.HttpHeaders.CONTENT_TYPE),
                response.getHeaders().getFirst(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION)
        );
    }

    @Override
    public DownloadStreamView downloadStream(UUID fileId, org.springframework.http.HttpHeaders clientHeaders, String accessToken) {
        String effectiveToken = accessToken;
        if (effectiveToken == null || effectiveToken.isBlank()) {
            effectiveToken = kernelSystemUserTokenProvider.getSystemUserAccessToken();
        }

        String path = "/api/files/" + fileId;
        org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> response = kernelHttpClient.downloadStream(
                path,
                clientHeaders,
                null,
                effectiveToken
        );
        return new DownloadStreamView(
                response.getBody(),
                response.getStatusCode(),
                response.getHeaders()
        );
    }
}
