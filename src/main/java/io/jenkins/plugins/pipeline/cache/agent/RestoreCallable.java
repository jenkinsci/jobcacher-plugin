package io.jenkins.plugins.pipeline.cache.agent;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.amazonaws.services.s3.model.S3Object;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.plugins.itemstorage.ItemStorage;

/**
 * Extracts an existing tar archive from S3 to a given {@link FilePath}.
 */
public class RestoreCallable extends AbstractMasterToAgentS3Callable {
    private final String[] restoreKeys;

    public RestoreCallable(ItemStorage<?> config, String... restoreKeys) {
        super(config);
        this.restoreKeys = restoreKeys;
    }

    @Override
    public Result invoke(File path, VirtualChannel channel) throws IOException, InterruptedException {
        // make sure that the restore path not exists yet or is a directory
        if (path.exists() && !path.isDirectory()) {
            return new ResultBuilder()
                    .withInfo("Cache not restored (path is not a directory)")
                    .build();
        }

        String key = cacheItemRepository().findKeyByRestoreKeys(restoreKeys);

        // make sure that the cache exists
        if (key == null) {
            return new ResultBuilder()
                    .withInfo("Cache not restored (no such key found)")
                    .build();
        }

        // do restore
        long startNanoTime = System.nanoTime();
        try (S3Object s3Object = cacheItemRepository().getS3Object(key);
             InputStream is = s3Object.getObjectContent()) {
            new FilePath(path).untarFrom(is, FilePath.TarCompression.NONE);
            // update last access timestamp
            cacheItemRepository().updateLastAccess(s3Object);
        }
        return new ResultBuilder()
                .withInfo(format("Cache restored successfully (%s)", key))
                .withInfo(performanceString(key, startNanoTime))
                .build();
    }


}
