package com.yowpainter.shared.kernel.adapter.dto;

/** Reponse de {@code GET /api/files/{id}/signed-url} : adresse publique du fichier. */
public record KernelSignedUrlResponseDto(String url) {
}
