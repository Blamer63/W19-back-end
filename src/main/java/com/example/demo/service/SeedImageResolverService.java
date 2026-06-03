package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SeedImageResolverService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Set<String> ALLOWED_HOSTS = Set.of("images.unsplash.com");
    private static final String SEED_IMAGE_PREFIX = "images/seed/";

    private final S3Service s3Service;
    private final HttpClient httpClient;
    private final Map<String, String> resolvedCache = new ConcurrentHashMap<>();

    public SeedImageResolverService(S3Service s3Service) {
        this.s3Service = s3Service;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<String> resolveSeedImageUrls(List<SeedImageSource> imageSources) {
        if (imageSources == null || imageSources.isEmpty()) {
            return List.of();
        }
        return imageSources.stream()
                .map(this::resolveSeedImageSource)
                .toList();
    }

    public List<String> resolveSeedImageUrls(List<String> sourceUrls, Set<String> allowedSourceUrls) {
        if (sourceUrls == null || sourceUrls.isEmpty()) {
            return List.of();
        }
        Set<String> allowedUrls = allowedSourceUrls == null ? Set.of() : allowedSourceUrls;
        return sourceUrls.stream()
                .map(sourceUrl -> resolveAllowedSeedImageUrl(sourceUrl, allowedUrls))
                .toList();
    }

    private String resolveAllowedSeedImageUrl(String sourceUrl, Set<String> allowedSourceUrls) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("Seed image URL must not be empty");
        }
        String normalizedUrl = sourceUrl.trim();
        if (s3Service.extractKey(normalizedUrl) != null) {
            return normalizedUrl;
        }
        if (!allowedSourceUrls.contains(normalizedUrl)) {
            throw new IllegalArgumentException("Seed image URL is not allowlisted");
        }
        return resolveSeedImageSource(SeedImageSource.jpg(normalizedUrl));
    }

    private String resolveSeedImageSource(SeedImageSource imageSource) {
        if (imageSource == null) {
            throw new IllegalArgumentException("Seed image source must not be null");
        }
        String normalizedUrl = imageSource.sourceUrl();
        if (s3Service.extractKey(normalizedUrl) != null) {
            return normalizedUrl;
        }
        validateUri(URI.create(normalizedUrl));
        validateSeedKey(imageSource.key());
        return resolvedCache.computeIfAbsent(normalizedUrl, ignored -> importSeedImage(imageSource));
    }

    private String importSeedImage(SeedImageSource imageSource) {
        String key = imageSource.key();
        if (s3Service.objectExists(key)) {
            String publicUrl = s3Service.getFileUrl(key);
            log.debug("starter-seed: reused existing seed image {} as {}", imageSource.sourceUrl(), publicUrl);
            return publicUrl;
        }

        URI uri = URI.create(imageSource.sourceUrl());
        SeedImage seedImage = downloadSeedImage(uri);
        String publicUrl = s3Service.uploadTrustedImageBytes(seedImage.bytes(), seedImage.contentType(), key);
        log.debug("starter-seed: imported seed image {} as {}", imageSource.sourceUrl(), publicUrl);
        return publicUrl;
    }

    private void validateSeedKey(String key) {
        if (key == null || key.isBlank() || !key.startsWith(SEED_IMAGE_PREFIX)) {
            throw new IllegalArgumentException("Seed image key must be under images/seed/");
        }
    }

    private void validateUri(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Seed image URL must use HTTPS");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!ALLOWED_HOSTS.contains(host)) {
            throw new IllegalArgumentException("Seed image host is not allowlisted");
        }
    }

    protected SeedImage downloadSeedImage(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Seed image download failed with status " + response.statusCode());
            }
            String contentType = normalizeContentType(response.headers()
                    .firstValue("Content-Type")
                    .orElse(""));
            return new SeedImage(response.body(), contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Seed image download failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Seed image download interrupted", e);
        }
    }

    private String normalizeContentType(String contentType) {
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    protected record SeedImage(byte[] bytes, String contentType) {
    }

    public record SeedImageSource(String sourceUrl, String key) {
        public SeedImageSource {
            if (sourceUrl == null || sourceUrl.isBlank()) {
                throw new IllegalArgumentException("Seed image URL must not be empty");
            }
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Seed image key must not be empty");
            }
            sourceUrl = sourceUrl.trim();
            key = key.trim();
        }

        public static SeedImageSource jpg(String sourceUrl) {
            String normalizedUrl = sourceUrl == null ? "" : sourceUrl.trim();
            return new SeedImageSource(
                    normalizedUrl,
                    SEED_IMAGE_PREFIX + sha256(normalizedUrl) + ".jpg");
        }
    }
}
