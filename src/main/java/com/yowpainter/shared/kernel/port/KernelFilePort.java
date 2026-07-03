package com.yowpainter.shared.kernel.port;

import java.util.UUID;

public interface KernelFilePort {

    FileView upload(UploadFileCommand command, String accessToken);

    DownloadFileView download(UUID fileId, String accessToken);

    DownloadStreamView downloadStream(UUID fileId, org.springframework.http.HttpHeaders clientHeaders, String accessToken);

    record UploadFileCommand(
            UUID organizationId,
            byte[] content,
            String fileName,
            String contentType,
            String documentType
    ) {
    }

    record FileView(
            UUID id,
            String fileName,
            String contentType,
            String downloadUrl
    ) {
    }

    record DownloadFileView(
            byte[] content,
            String contentType,
            String contentDisposition
    ) {
    }

    record DownloadStreamView(
            org.springframework.core.io.Resource resource,
            org.springframework.http.HttpStatusCode statusCode,
            org.springframework.http.HttpHeaders headers
    ) {
    }
}
