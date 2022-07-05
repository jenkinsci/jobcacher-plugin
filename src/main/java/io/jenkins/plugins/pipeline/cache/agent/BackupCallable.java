package io.jenkins.plugins.pipeline.cache.agent;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.plugins.itemstorage.ItemStorage;

/**
 * Creates a tar archive of a given {@link FilePath} and uploads it to S3.
 */
public class BackupCallable extends AbstractMasterToAgentS3Callable {
    private final String key;
    private final String filter;

    /**
     * @param config S3 instance and bucket name
     * @param key the key used for this backup
     * @param filter Ant file pattern mask, like <b>**&#47;*.java</b> which is applied to the path.
     */
    public BackupCallable(ItemStorage<?> config, String key, String filter) {
        super(config);
        this.key = key;
        this.filter = filter == null || filter.isEmpty() ? "**/*" : filter;
    }

    @Override
    public Result invoke(File path, VirtualChannel channel) throws IOException, InterruptedException {
        // make sure that path exists
        if (!path.exists()) {
            return new ResultBuilder()
                    .withInfo("Cache not saved (path not exists)")
                    .build();
        }

        // make sure that path is a directory
        else if (!path.isDirectory()) {
            return new ResultBuilder()
                    .withInfo("Cache not saved (path is not a directory)")
                    .build();
        }

        // make sure that cache not exists yet
        if (cacheItemRepository().exists(key)) {
            return new ResultBuilder()
                    .withInfo(format("Cache not saved (%s already exists)", key))
                    .build();
        }

        // do backup
        long start = System.nanoTime();
        try (OutputStream out = cacheItemRepository().createObjectOutputStream(key)) {
            new FilePath(path).tar(out, filter);
        }
        return new ResultBuilder()
                .withInfo(format("Cache saved successfully (%s)", key))
                .withInfo(performanceString(key, start))
                .build();
    }

}
