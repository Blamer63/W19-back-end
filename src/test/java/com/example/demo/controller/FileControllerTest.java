package com.example.demo.controller;

import com.example.demo.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileControllerTest {

    private StubS3Service s3Service;
    private FileController fileController;

    @BeforeEach
    void setUp() {
        s3Service = new StubS3Service();
        fileController = new FileController(s3Service);
    }

    @Test
    void deleteFileRejectsEntityOwnedImageKeys() {
        var response = fileController.deleteFile("images/avatar.jpg");

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Only standalone audio and video keys can be deleted here"));
        assertThat(s3Service.deletedKey).isNull();
    }

    @Test
    void deleteFileAllowsStandaloneAudioKeys() {
        var response = fileController.deleteFile("audio/voice.mp3");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(Map.of("message", "File deleted successfully"));
        assertThat(s3Service.deletedKey).isEqualTo("audio/voice.mp3");
    }

    private static class StubS3Service extends S3Service {
        private String deletedKey;

        private StubS3Service() {
            super(null);
        }

        @Override
        public boolean isStandaloneMediaKey(String key) {
            return key != null && (key.startsWith("audio/") || key.startsWith("videos/"));
        }

        @Override
        public void deleteFile(String key) {
            this.deletedKey = key;
        }
    }
}
