package com.example.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeedImageResolverServiceTest {

    private static final String SOURCE_URL =
            "https://images.unsplash.com/photo-1506973035872-a4ec16b8e8d9?w=900&h=600&fit=crop";
    private static final SeedImageResolverService.SeedImageSource SOURCE =
            SeedImageResolverService.SeedImageSource.jpg(SOURCE_URL);

    @Mock
    private S3Service s3Service;

    private TestSeedImageResolverService resolver;

    @BeforeEach
    void setUp() {
        resolver = new TestSeedImageResolverService(s3Service);
    }

    @Test
    void resolveSeedImageUrlsImportsMissingSeedImageToDeterministicKey() {
        when(s3Service.extractKey(SOURCE_URL)).thenReturn(null);
        when(s3Service.objectExists(SOURCE.key())).thenReturn(false);
        when(s3Service.uploadTrustedImageBytes(
                argThat(bytes -> Arrays.equals(bytes, "image-bytes".getBytes())),
                eq("image/jpeg"),
                eq(SOURCE.key())))
                .thenReturn("https://cdn.example.test/images/seed/demo.jpg");

        List<String> result = resolver.resolveSeedImageUrls(List.of(SOURCE, SOURCE));

        assertThat(result).containsExactly(
                "https://cdn.example.test/images/seed/demo.jpg",
                "https://cdn.example.test/images/seed/demo.jpg");
        assertThat(resolver.downloadCount).isEqualTo(1);
        verify(s3Service, times(1)).objectExists(SOURCE.key());
        verify(s3Service, times(1)).uploadTrustedImageBytes(any(), eq("image/jpeg"), eq(SOURCE.key()));
    }

    @Test
    void resolveSeedImageUrlsReusesExistingSeedObjectWithoutDownloadOrUpload() {
        when(s3Service.extractKey(SOURCE_URL)).thenReturn(null);
        when(s3Service.objectExists(SOURCE.key())).thenReturn(true);
        when(s3Service.getFileUrl(SOURCE.key())).thenReturn("https://cdn.example.test/" + SOURCE.key());

        List<String> result = resolver.resolveSeedImageUrls(List.of(SOURCE));

        assertThat(result).containsExactly("https://cdn.example.test/" + SOURCE.key());
        assertThat(resolver.downloadCount).isZero();
        verify(s3Service, never()).uploadTrustedImageBytes(any(), any(), any());
    }

    @Test
    void resolveSeedImageUrlsRejectsSourceOutsideAllowlist() {
        when(s3Service.extractKey(SOURCE_URL)).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolveSeedImageUrls(List.of(SOURCE_URL), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Seed image URL is not allowlisted");

        assertThat(resolver.downloadCount).isZero();
        verify(s3Service, never()).uploadTrustedImageBytes(any(), any(), any());
    }

    @Test
    void resolveSeedImageUrlsRejectsManifestSourceOutsideAllowedHost() {
        SeedImageResolverService.SeedImageSource source =
                SeedImageResolverService.SeedImageSource.jpg("https://example.com/photo.jpg");
        when(s3Service.extractKey(source.sourceUrl())).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolveSeedImageUrls(List.of(source)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Seed image host is not allowlisted");

        assertThat(resolver.downloadCount).isZero();
        verify(s3Service, never()).uploadTrustedImageBytes(any(), any(), any());
    }

    @Test
    void resolveSeedImageUrlsLeavesManagedObjectStoreUrlsUnchanged() {
        String managedUrl = "https://cdn.example.test/images/seed/demo.jpg";
        when(s3Service.extractKey(managedUrl)).thenReturn("images/seed/demo.jpg");

        List<String> result = resolver.resolveSeedImageUrls(List.of(
                new SeedImageResolverService.SeedImageSource(managedUrl, "images/seed/demo.jpg")));

        assertThat(result).containsExactly(managedUrl);
        assertThat(resolver.downloadCount).isZero();
        verify(s3Service, never()).uploadTrustedImageBytes(any(), any(), any());
    }

    private static class TestSeedImageResolverService extends SeedImageResolverService {
        private int downloadCount;

        private TestSeedImageResolverService(S3Service s3Service) {
            super(s3Service);
        }

        @Override
        protected SeedImage downloadSeedImage(URI uri) {
            downloadCount++;
            return new SeedImage("image-bytes".getBytes(), "image/jpeg");
        }
    }
}
