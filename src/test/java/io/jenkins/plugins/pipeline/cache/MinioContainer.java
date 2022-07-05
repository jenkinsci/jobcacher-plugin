package io.jenkins.plugins.pipeline.cache;

import java.util.UUID;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

public class MinioContainer extends GenericContainer<MinioContainer> {

    public MinioContainer() {
        this("minio/minio");
    }

    public MinioContainer(String dockerImageName) {
        super(dockerImageName);

        setWaitStrategy(Wait.forHttp("/minio/health/ready").forStatusCode(200));

        withEnv("MINIO_ROOT_USER", UUID.randomUUID().toString());
        withEnv("MINIO_ROOT_PASSWORD", UUID.randomUUID().toString());
        withCommand("server /data");
        withExposedPorts(9000);
        withNetwork(Network.newNetwork());  // we need a dedicated network otherwise mc cannot participate
    }

    public String accessKey() {
        return getEnvMap().get("MINIO_ROOT_USER");
    }

    public String secretKey() {
        return getEnvMap().get("MINIO_ROOT_PASSWORD");
    }

    public String getExternalAddress() {
        return "http://localhost:" + getMappedPort(9000);
    }
}