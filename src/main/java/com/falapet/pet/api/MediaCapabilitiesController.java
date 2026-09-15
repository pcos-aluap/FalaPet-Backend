package com.falapet.pet.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ApiResponse;

@RestController
@RequestMapping(path = "/api/v1/media", produces = ApiMediaTypes.APPLICATION_JSON_VALUE)
public final class MediaCapabilitiesController {
    @GetMapping("/capabilities")
    public ApiResponse<Capabilities> capabilities() {
        return ApiResponse.of(new Capabilities(
                new PetPhoto(false, List.of("image/jpeg", "image/png", "image/webp"),
                        5_242_880, 128, 128, 4096, 4096, true),
                new ButtonAudio(false, List.of("audio/mp4"), 5_242_880, 10_000, true)));
    }

    public record Capabilities(PetPhoto petPhoto, ButtonAudio buttonAudio) {}
    public record PetPhoto(boolean enabled, List<String> acceptedMediaTypes, long maxSizeBytes,
            int minWidth, int minHeight, int maxWidth, int maxHeight, boolean sha256Required) {}
    public record ButtonAudio(boolean enabled, List<String> acceptedMediaTypes, long maxSizeBytes,
            int maxDurationMs, boolean sha256Required) {}
}
