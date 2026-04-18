package com.beachassistant.web.controller;

import com.beachassistant.app.usecase.BeachResolverUseCase;
import com.beachassistant.app.usecase.BeachStatusUseCase;
import com.beachassistant.app.usecase.CameraUseCase;
import com.beachassistant.app.usecase.LifeguardUseCase;
import com.beachassistant.app.usecase.JellyfishUseCase;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.common.exception.CameraUnavailableException;
import com.beachassistant.decision.freshness.FreshnessService;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.persistence.entity.*;
import com.beachassistant.web.dto.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/beaches")
public class BeachController {

    private final BeachResolverUseCase beachResolver;
    private final BeachStatusUseCase statusUseCase;
    private final CameraUseCase cameraUseCase;
    private final LifeguardUseCase lifeguardUseCase;
    private final JellyfishUseCase jellyfishUseCase;
    private final FreshnessService freshnessService;

    public BeachController(BeachResolverUseCase beachResolver,
                            BeachStatusUseCase statusUseCase,
                            CameraUseCase cameraUseCase,
                            LifeguardUseCase lifeguardUseCase,
                            JellyfishUseCase jellyfishUseCase,
                            FreshnessService freshnessService) {
        this.beachResolver = beachResolver;
        this.statusUseCase = statusUseCase;
        this.cameraUseCase = cameraUseCase;
        this.lifeguardUseCase = lifeguardUseCase;
        this.jellyfishUseCase = jellyfishUseCase;
        this.freshnessService = freshnessService;
    }

    @GetMapping
    public List<BeachListItemDto> listBeaches() {
        return beachResolver.listAll().stream()
                .map(b -> BeachListItemDto.builder()
                        .id(b.getSlug())
                        .displayName(b.getDisplayName())
                        .city(b.getCity().getName())
                        .aliases(b.getAliases())
                        .hasCamera(b.isHasCamera())
                        .hasLifeguards(b.isHasLifeguards())
                        .hasJellyfishSource(b.isHasJellyfishSource())
                        .isActive(b.isActive())
                        .build())
                .toList();
    }

    @GetMapping("/{slug}/status")
    public ResponseEntity<BeachStatusDto> getStatus(@PathVariable String slug, WebRequest webRequest) {
        BeachDecision decision = statusUseCase.getStatus(slug);
        BeachStatusDto body = BeachStatusDto.builder()
                .beach(decision.getBeachDisplayName())
                .city(decision.getCity())
                .recommendation(decision.getRecommendation())
                .confidence(decision.getConfidence())
                .reasons(decision.getReasonCodes())
                .summary(decision.getHumanSummary())
                .freshnessStatus(decision.getFreshnessStatus())
                .updatedAt(decision.getGeneratedAt())
                .validFrom(decision.getEffectiveFrom())
                .validTo(decision.getEffectiveTo())
                .sources(decision.getSourceFreshness())
                .sourceCapturedAt(decision.getSourceCapturedAt())
                .missingSources(decision.getMissingSourceTypes())
                .seaTemperatureC(decision.getSeaTemperatureC())
                .windDirection(decision.getWindDirection())
                .windSpeedMps(decision.getWindSpeedMps())
                .build();

        String etag = computeEtag(decision);
        long lastModifiedMillis = decision.getGeneratedAt() != null
                ? decision.getGeneratedAt().toInstant().toEpochMilli()
                : System.currentTimeMillis();
        if (webRequest.checkNotModified(etag, lastModifiedMillis)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(etag);
        if (decision.getGeneratedAt() != null) {
            headers.set(HttpHeaders.LAST_MODIFIED,
                    DateTimeFormatter.RFC_1123_DATE_TIME.format(
                            decision.getGeneratedAt().withZoneSameInstant(ZoneOffset.UTC)));
        }
        headers.setCacheControl("public, max-age=60");
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private static String computeEtag(BeachDecision decision) {
        String raw = decision.getBeachSlug()
                + "|" + decision.getRecommendation()
                + "|" + decision.getConfidence()
                + "|" + (decision.getGeneratedAt() == null ? "" : decision.getGeneratedAt().toInstant().toEpochMilli());
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return "\"" + HexFormat.of().formatHex(d).substring(0, 16) + "\"";
        } catch (NoSuchAlgorithmException e) {
            return "\"" + Integer.toHexString(raw.hashCode()) + "\"";
        }
    }

    @GetMapping("/{slug}/hours")
    public ResponseEntity<LifeguardHoursDto> getHours(@PathVariable String slug) {
        return ResponseEntity.ok(lifeguardUseCase.getHours(slug));
    }

    @GetMapping("/{slug}/jellyfish")
    public ResponseEntity<JellyfishDto> getJellyfish(@PathVariable String slug) {
        return ResponseEntity.ok(jellyfishUseCase.getJellyfish(slug));
    }

    @GetMapping("/{slug}/camera")
    public ResponseEntity<CameraDto> getCamera(@PathVariable String slug) {
        CameraEndpointEntity camera = cameraUseCase.getActiveCamera(slug);
        return ResponseEntity.ok(CameraDto.builder()
                .beach(slug)
                .providerName(camera.getProviderName())
                .liveUrl(camera.getLiveUrl())
                .isActive(camera.isActive())
                .healthStatus(camera.getHealthStatus())
                .lastCheckedAt(camera.getLastCheckedAt())
                .build());
    }

    @GetMapping("/{slug}/camera/snapshot")
    public ResponseEntity<CameraSnapshotDto> getCameraSnapshot(@PathVariable String slug) {
        Optional<CameraSnapshotEntity> snapshot = cameraUseCase.getLatestSnapshot(slug);
        return snapshot.map(s -> ResponseEntity.ok(CameraSnapshotDto.builder()
                        .beach(slug)
                        .capturedAt(s.getCapturedAt())
                        .storageUrl(s.getStorageUrl())
                        .width(s.getWidth())
                        .height(s.getHeight())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }
}
