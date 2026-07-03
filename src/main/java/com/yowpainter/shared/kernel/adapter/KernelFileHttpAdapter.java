package com.yowpainter.shared.kernel.adapter;

import com.yowpainter.config.KernelProperties;
import com.yowpainter.shared.kernel.KernelHttpClient;
import com.yowpainter.shared.kernel.KernelSystemUserTokenProvider;
import com.yowpainter.shared.kernel.adapter.dto.KernelStoredFileResponseDto;
import com.yowpainter.shared.kernel.port.KernelFilePort;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class KernelFileHttpAdapter implements KernelFilePort {

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
        KernelStoredFileResponseDto response = kernelHttpClient.uploadMultipart(
                "/api/files",
                command.content(),
                command.fileName(),
                command.contentType(),
                command.documentType(),
                KernelStoredFileResponseDto.class,
                command.organizationId(),
                accessToken
        );
        String baseUrl = properties.baseUrl() == null ? "" : properties.baseUrl().replaceAll("/$", "");
        return new FileView(
                response.id(),
                response.fileName(),
                response.contentType(),
                baseUrl + "/api/files/" + response.id()
        );
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
