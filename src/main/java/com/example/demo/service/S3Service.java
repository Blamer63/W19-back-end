package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.buckets.customer}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Uploads a file to S3 and returns its public URL.
     *
     * @param file   the file sent from the client
     * @param folder logical folder name: "images", "audio", or "videos"
     * @return the full public URL of the uploaded file
     */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        // Build a unique key so two users uploading "photo.jpg" never collide
        String key = folder + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

        // Return the standard S3 URL format
        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
    }

    /**
     * Deletes a file from S3 using its key (the path portion of the URL).
     *
     * @param key e.g. "images/abc-123-photo.jpg"
     */
    public void deleteFile(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.deleteObject(request);
    }

    /**
     * Reconstructs the public URL from a stored key.
     * Useful when you only store the key in the database, not the full URL.
     *
     * @param key e.g. "images/abc-123-photo.jpg"
     * @return full public URL
     */
    public String getFileUrl(String key) {
        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
    }

    /**
     * Extracts the S3 key from a full public URL produced by this service.
     * Returns null if the URL does not belong to this bucket.
     *
     * @param url e.g. "https://bucket.s3.region.amazonaws.com/images/uuid-photo.jpg"
     * @return the key portion, e.g. "images/uuid-photo.jpg", or null
     */
    public String extractKey(String url) {
        if (url == null) return null;
        String prefix = "https://" + bucketName + ".s3." + region + ".amazonaws.com/";
        if (url.startsWith(prefix)) {
            return url.substring(prefix.length());
        }
        return null;
    }
}
