package com.chess.backend.storage;

import com.chess.backend.config.AppProperties;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3FileStorage implements FileStorage {

    private final AppProperties properties;
    private final S3Client client;

    public S3FileStorage(AppProperties properties) {
        this.properties = properties;
        if (!"s3".equalsIgnoreCase(properties.storageProvider())) {
            throw new IllegalStateException("Only the S3-compatible storage provider is configured");
        }
        this.client = S3Client.builder()
                .region(Region.of(properties.s3Region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.s3AccessKey(), properties.s3SecretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.s3PathStyle()).build())
                .endpointOverride(URI.create(properties.s3Endpoint()))
                .build();
    }

    @PostConstruct
    void ensureBucketExists() {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                client.headBucket(HeadBucketRequest.builder().bucket(properties.s3Bucket()).build());
                return;
            } catch (S3Exception exception) {
                if (exception.statusCode() == 404) {
                    client.createBucket(CreateBucketRequest.builder().bucket(properties.s3Bucket()).build());
                    return;
                }
                lastFailure = exception;
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
            try { Thread.sleep(1500); }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for object storage", exception);
            }
        }
        throw new IllegalStateException("Could not initialize object storage", lastFailure);
    }

    @Override
    public String store(String category, InputStream input, long size, String contentType) {
        String key = category + "/" + UUID.randomUUID();
        client.putObject(PutObjectRequest.builder().bucket(properties.s3Bucket()).key(key).contentType(contentType).build(),
                RequestBody.fromInputStream(input, size));
        return key;
    }

    @Override
    public StoredFile retrieve(String key) {
        ResponseInputStream<GetObjectResponse> response = client.getObject(
                GetObjectRequest.builder().bucket(properties.s3Bucket()).key(key).build());
        return new StoredFile(response, response.response().contentType(), response.response().contentLength());
    }

    @Override
    public void delete(String key) {
        if (key != null && !key.isBlank()) {
            client.deleteObject(DeleteObjectRequest.builder().bucket(properties.s3Bucket()).key(key).build());
        }
    }
}
