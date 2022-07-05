package io.jenkins.plugins.pipeline.cache.s3;

import java.io.Serializable;

public class S3Config implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String username;
    private final String password;
    private final String region;
    private final String endpoint;
    private final String bucket;

    public S3Config(String username, String password, String region, String endpoint, String bucket) {
        this.username = username;
        this.password = password;
        this.region = region;
        this.endpoint = endpoint;
        this.bucket = bucket;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
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
