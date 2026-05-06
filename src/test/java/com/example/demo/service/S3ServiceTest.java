package com.example.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3ServiceTest {

    private CapturingS3Client capturingS3Client;
    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        capturingS3Client = new CapturingS3Client();
        s3Service = new S3Service(capturingS3Client.client());
        ReflectionTestUtils.setField(s3Service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(s3Service, "region", "ap-southeast-2");
        ReflectionTestUtils.setField(s3Service, "cloudfrontDomain", "cdn.example.test");
    }

    @Test
    void uploadFileReturnsCloudFrontUrlAndSanitizesOriginalFilename() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "..\\nested/my photo @ 2026.png",
                "image/png",
                "image-bytes".getBytes());

        String url = s3Service.uploadFile(file, "images");

        String key = capturingS3Client.putObjectRequest.key();
        assertThat(key).startsWith("images/");
        assertThat(key).endsWith("-my_photo_2026.png");
        assertThat(url).isEqualTo("https://cdn.example.test/" + key);
    }

    @Test
    void uploadFileRejectsUnknownFolders() {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "text".getBytes());

        assertThatThrownBy(() -> s3Service.uploadFile(file, "documents"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid S3 folder");
    }

    @Test
    void getFileUrlUsesCloudFrontDomain() {
        assertThat(s3Service.getFileUrl("audio/example.mp3"))
                .isEqualTo("https://cdn.example.test/audio/example.mp3");
    }

    @Test
    void extractKeySupportsOldS3UrlsAndNewCloudFrontUrls() {
        assertThat(s3Service.extractKey("https://test-bucket.s3.ap-southeast-2.amazonaws.com/images/avatar.jpg"))
                .isEqualTo("images/avatar.jpg");
        assertThat(s3Service.extractKey("https://cdn.example.test/videos/clip.mp4"))
                .isEqualTo("videos/clip.mp4");
        assertThat(s3Service.extractKey("https://other.example.test/images/avatar.jpg"))
                .isNull();
    }

    @Test
    void isStandaloneMediaKeyOnlyAllowsAudioAndVideos() {
        assertThat(s3Service.isStandaloneMediaKey("audio/voice.mp3")).isTrue();
        assertThat(s3Service.isStandaloneMediaKey("videos/clip.mp4")).isTrue();
        assertThat(s3Service.isStandaloneMediaKey("images/avatar.jpg")).isFalse();
        assertThat(s3Service.isStandaloneMediaKey(null)).isFalse();
    }

    @Test
    void sanitizeOriginalFilenameHandlesUnsafeAndBlankNames() {
        assertThat(s3Service.sanitizeOriginalFilename("../secret file!!.jpg")).isEqualTo("secret_file_.jpg");
        assertThat(s3Service.sanitizeOriginalFilename("...")).isEqualTo("file");
        assertThat(s3Service.sanitizeOriginalFilename(null)).isEqualTo("file");
    }

    private static class CapturingS3Client {
        private PutObjectRequest putObjectRequest;

        private S3Client client() {
            return (S3Client) Proxy.newProxyInstance(
                    S3Client.class.getClassLoader(),
                    new Class<?>[]{S3Client.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("putObject")) {
                            putObjectRequest = (PutObjectRequest) args[0];
                            assertThat(args[1]).isInstanceOf(RequestBody.class);
                            return PutObjectResponse.builder().build();
                        }
                        if (method.getName().equals("serviceName")) {
                            return "s3";
                        }
                        if (method.getName().equals("close")) {
                            return null;
                        }
                        if (method.getName().equals("toString")) {
                            return "capturing-s3-client";
                        }
                        throw new UnsupportedOperationException("Unexpected S3Client method: " + method.getName());
                    });
        }
    }
}
