package minio;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;

import static java.lang.String.format;

public class MinioMcContainer extends GenericContainer<MinioMcContainer> {

    private final MinioContainer minio;

    public MinioMcContainer(MinioContainer minio) {
        super("minio/mc");
        this.minio = minio;
        dependsOn(minio);
        withNetwork(minio.getNetwork());
        withCreateContainerCmdModifier(c -> c.withTty(true).withEntrypoint("/bin/sh"));
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        try {
            execSecure("mc config host add test-minio http://%s:9000 %s %s",
                    minio.getNetworkAliases().get(0),
                    minio.accessKey(),
                    minio.secretKey());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public ExecResult execSecure(String command, Object... args) throws IOException, InterruptedException {
        ExecResult result = exec(command, args);
        if (result.getExitCode() != 0) {
            throw new AssertionError(result.getStderr());
        }
        return result;
    }

    public ExecResult exec(String command, Object... args) throws IOException, InterruptedException {
        return execInContainer("/bin/sh", "-c", format(command, args));
    }

    public void deleteBucket(String bucket) throws IOException, InterruptedException {
        exec("mc rb test-minio/%s --force", bucket);
    }

    public void createBucket(String bucket) throws IOException, InterruptedException {
        execSecure("mc mb test-minio/%s", bucket);
    }

    public void createObject(String bucket, String key, String content) throws IOException, InterruptedException {
        execSecure("echo -n \"%s\" | mc pipe test-minio/%s/%s", content, bucket, key);
    }
}