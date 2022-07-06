package io.jenkins.plugins.pipeline.cache.s3;

import java.io.Serializable;

import com.amazonaws.auth.AWSCredentialsProvider;

public class S3Config implements Serializable {

    private static final long serialVersionUID = 1L;

    private final AWSCredentialsProvider credentials;
    private final String region;
    private final String endpoint;
    private final String bucket;

    public S3Config(AWSCredentialsProvider credentials, String region, String endpoint, String bucket) {
        this.credentials = credentials;
        this.region = region;
        this.endpoint = endpoint;
        this.bucket = bucket;
    }

    public AWSCredentialsProvider getCredentials() {
        return credentials;
    }

    public String getRegion() {
        return region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getBucket() {
        return bucket;
    }

}
