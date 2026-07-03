package com.yowpainter.modules.file.application.service;

import com.yowpainter.modules.file.infrastructure.adapter.in.web.dto.FileResponseDto;
import com.yowpainter.shared.kernel.port.KernelFilePort;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

public interface FileService {
    FileResponseDto uploadFile(MultipartFile file, String documentType, String accessToken);
    KernelFilePort.DownloadFileView downloadFile(UUID fileId, String accessToken);
    KernelFilePort.DownloadStreamView downloadFileStream(UUID fileId, org.springframework.http.HttpHeaders clientHeaders, String accessToken);
}
