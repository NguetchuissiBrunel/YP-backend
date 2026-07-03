package com.yowpainter.modules.file.infrastructure.adapter.in.web;

import com.yowpainter.modules.file.application.service.FileService;
import com.yowpainter.modules.file.infrastructure.adapter.in.web.dto.FileResponseDto;
import com.yowpainter.shared.kernel.port.KernelFilePort;
import com.yowpainter.shared.security.KernelAccessTokenResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "Files BFF", description = "Gestion des fichiers BFF")
public class FileController {

    private final FileService fileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader un fichier (BFF)")
    public ResponseEntity<FileResponseDto> uploadFile(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", defaultValue = "GENERAL") String documentType) {
        String accessToken = KernelAccessTokenResolver.requireAccessToken(authentication);
        FileResponseDto response = fileService.uploadFile(file, documentType, accessToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "Telecharger/Diffuser un fichier (BFF)")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFile(
            Authentication authentication,
            @PathVariable("fileId") UUID fileId,
            @RequestHeader org.springframework.http.HttpHeaders clientHeaders) {
        String accessToken = KernelAccessTokenResolver.resolveAccessToken(authentication);
        KernelFilePort.DownloadStreamView streamView = fileService.downloadFileStream(fileId, clientHeaders, accessToken);

        HttpHeaders headers = new HttpHeaders();
        streamView.headers().forEach((name, values) -> {
            if (!name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING) &&
                !name.equalsIgnoreCase(HttpHeaders.CONNECTION) &&
                !name.equalsIgnoreCase(HttpHeaders.HOST)) {
                headers.put(name, values);
            }
        });

        return ResponseEntity.status(streamView.statusCode())
                .headers(headers)
                .body(streamView.resource());
    }
}
