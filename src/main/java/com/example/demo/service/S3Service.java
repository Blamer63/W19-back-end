package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
public class S3Service {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024; // 5 MB
    private static final int MAX_FILENAME_LENGTH = 120;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final Set<String> ALLOWED_FOLDERS = Set.of("images", "audio", "videos");
    private static final Set<String> STANDALONE_MEDIA_FOLDERS = Set.of("audio", "videos");

    private final S3Client s3Client;

    @Value("${aws.s3.buckets.customer}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.cloudfront.domain}")
    private String cloudfrontDomain;

    @Value("${aws.s3.mock:false}")
    private boolean mockMode;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Validates that a file is an acceptable image before uploading.
     * Throws IllegalArgumentException if validation fails.
     */
    public void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file must not be empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Invalid image type. Allowed: jpeg, png, gif, webp");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("Image exceeds the 5 MB limit");
        }
    }

    /**
     * Uploads a file to S3 and returns its public URL.
     *
     * @param file   the file sent from the client
     * @param folder logical folder name: "images", "audio", or "videos"
     * @return the full public URL of the uploaded file
     */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        if (!ALLOWED_FOLDERS.contains(folder)) {
            throw new IllegalArgumentException("Invalid S3 folder");
        }

        String key = folder + "/" + UUID.randomUUID() + "-" + sanitizeOriginalFilename(file.getOriginalFilename());

        if (mockMode) {
            return "https://mock-s3.local/" + key;
        }

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

        return "https://" + cloudfrontDomain + "/" + key;
    }

    /**
     * Deletes a file from S3 using its key (the path portion of the URL).
     *
     * @param key e.g. "images/abc-123-photo.jpg"
     */
    public void deleteFile(String key) {
        if (mockMode) {
            return;
        }
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
        return "https://" + cloudfrontDomain + "/" + key;
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
        String s3Prefix = "https://" + bucketName + ".s3." + region + ".amazonaws.com/";
        String cloudfrontPrefix = "https://" + cloudfrontDomain + "/";
        if (url.startsWith(s3Prefix)) {
            return url.substring(s3Prefix.length());
        }
        if (url.startsWith(cloudfrontPrefix)) {
            return url.substring(cloudfrontPrefix.length());
        }
        return null;
    }

    /**
     * Returns true when a key belongs to the generic audio/video upload area.
     * Image keys are owned by profile, post, or message records and should be
     * deleted through those entity workflows.
     */
    public boolean isStandaloneMediaKey(String key) {
        if (key == null) {
            return false;
        }
        return STANDALONE_MEDIA_FOLDERS.stream().anyMatch(folder -> key.startsWith(folder + "/"));
    }

    String sanitizeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }

        String filename = originalFilename.replace('\\', '/');
        int slashIndex = filename.lastIndexOf('/');
        if (slashIndex >= 0) {
            filename = filename.substring(slashIndex + 1);
        }

        filename = filename.replaceAll("[^A-Za-z0-9._-]", "_")
                .replaceAll("_+", "_");

        while (filename.startsWith(".")) {
            filename = filename.substring(1);
        }

        if (filename.isBlank()) {
            filename = "file";
        }

        if (filename.length() > MAX_FILENAME_LENGTH) {
            filename = filename.substring(filename.length() - MAX_FILENAME_LENGTH);
        }

        return filename;
    }
}
