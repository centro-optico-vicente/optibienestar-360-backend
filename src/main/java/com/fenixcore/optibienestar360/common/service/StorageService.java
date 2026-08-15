package com.fenixcore.optibienestar360.common.service;

import lombok.RequiredArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

/**
 * Thin wrapper over the S3 SDK pointed at Cloudflare R2. Two buckets are in
 * play (spec {@code .ai/specs/08-storage-r2.md} §1): a private one for
 * {@code CONFIDENTIAL}/{@code INTERNAL}/{@code TEMPORARY} content (accessed
 * only via presigned URL) and a public one for {@code PUBLIC} content
 * (served directly, no presigning). Every method has a private-bucket
 * overload (kept for {@code PaymentsService}, which predates the
 * two-bucket design) plus an explicit-bucket overload for callers that need
 * to target either bucket — {@code AttachedFileService} in particular.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.r2.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Getter
    @Value("${storage.r2.bucket}")
    private String bucket;

    @Getter
    @Value("${storage.r2.public-bucket:${storage.r2.bucket}}")
    private String publicBucket;

    /** Base URL objects in the public bucket are served from — e.g. an R2 public custom domain. */
    @Getter
    @Value("${storage.r2.public-base-url:}")
    private String publicBaseUrl;

    public void upload(String key, InputStream data, long contentLength, String contentType) {
        upload(bucket, key, data, contentLength, contentType);
    }

    public void upload(String targetBucket, String key, InputStream data, long contentLength, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(targetBucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(data, contentLength));
        log.debug("Uploaded {}/{}", targetBucket, key);
    }

    public InputStream download(String key) {
        return download(bucket, key);
    }

    public InputStream download(String sourceBucket, String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(sourceBucket)
                .key(key)
                .build();
        return s3Client.getObject(request);
    }

    public String generatePresignedUrl(String key, Duration expiration) {
        return generatePresignedUrl(bucket, key, expiration);
    }

    public String generatePresignedUrl(String sourceBucket, String key, Duration expiration) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(sourceBucket)
                        .key(key)
                        .build())
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public void delete(String key) {
        delete(bucket, key);
    }

    public void delete(String targetBucket, String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(targetBucket)
                .key(key)
                .build());
        log.debug("Deleted {}/{}", targetBucket, key);
    }

    public boolean exists(String key) {
        return exists(bucket, key);
    }

    public boolean exists(String targetBucket, String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /**
     * Server-side copy between (possibly different) buckets — the closest
     * R2/S3 equivalent to a symbolic link, used by {@code AttachedFileService}
     * to publish/unpublish a file between the private and public buckets
     * without the caller re-uploading the bytes.
     */
    public void copy(String sourceBucket, String sourceKey, String destBucket, String destKey) {
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(sourceBucket)
                .sourceKey(sourceKey)
                .destinationBucket(destBucket)
                .destinationKey(destKey)
                .build());
        log.debug("Copied {}/{} -> {}/{}", sourceBucket, sourceKey, destBucket, destKey);
    }
}
