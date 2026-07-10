package com.yowpainter.modules.auth.infrastructure.adapter.in.web;

import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.BuyerProfileResponse;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.BuyerUpdateRequest;
import com.yowpainter.shared.security.AuthenticatedUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.UpdateProfilePictureRequest;

@RestController
@RequestMapping("/api/buyer")
@RequiredArgsConstructor
@Tag(name = "Buyer Profile", description = "Gestion du profil Acheteur")
public class BuyerController {

    private final AppUserRepositoryPort userRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @GetMapping("/me")
    @Operation(summary = "Récupérer le profil de l'acheteur connecté")
    public ResponseEntity<BuyerProfileResponse> getMe(Authentication authentication) {
        AppUser user = authenticatedUserResolver.requireBuyer(authentication);
        return ResponseEntity.ok(mapToResponse(user));
    }

    @PutMapping("/me")
    @Operation(summary = "Mettre à jour le profil de l'acheteur")
    public ResponseEntity<BuyerProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody BuyerUpdateRequest request) {
        AppUser user = authenticatedUserResolver.requireBuyer(authentication);

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setBio(request.getBio());

        return ResponseEntity.ok(mapToResponse(userRepository.save(user)));
    }

    @PutMapping("/me/profile-picture")
    @Operation(summary = "Mettre à jour la photo de profil de l'acheteur")
    public ResponseEntity<BuyerProfileResponse> updateProfilePicture(
            Authentication authentication,
            @Valid @RequestBody UpdateProfilePictureRequest request) {
        AppUser user = authenticatedUserResolver.requireBuyer(authentication);

        user.setProfilePictureUrl(com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(request.getProfilePictureUrl()));

        return ResponseEntity.ok(mapToResponse(userRepository.save(user)));
    }

    private BuyerProfileResponse mapToResponse(AppUser user) {
        return BuyerProfileResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .profilePictureUrl(com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(user.getProfilePictureUrl()))
                .bio(user.getBio())
                .role(user.getRole().name())
                .build();
    }
}
