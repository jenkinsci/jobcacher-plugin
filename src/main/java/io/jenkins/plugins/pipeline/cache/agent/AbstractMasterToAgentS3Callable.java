package io.jenkins.plugins.pipeline.cache.agent;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.jenkins.cli.shaded.org.apache.commons.lang.NotImplementedException;
import io.jenkins.plugins.pipeline.cache.s3.CacheItemRepository;
import io.jenkins.plugins.pipeline.cache.s3.S3Config;
import jenkins.MasterToSlaveFileCallable;
import jenkins.plugins.itemstorage.ItemStorage;
import jenkins.plugins.itemstorage.s3.NonAWSS3ItemStorage;

/**
 * Base class for S3 related operations. Note: The class is constructed on the master node but method are executed on the agent.
 */
public abstract class AbstractMasterToAgentS3Callable extends MasterToSlaveFileCallable<AbstractMasterToAgentS3Callable.Result> {

    private final S3Config s3Config;
    private volatile CacheItemRepository cacheItemRepository;

    protected AbstractMasterToAgentS3Callable(ItemStorage<?> storage) {
        if (storage instanceof NonAWSS3ItemStorage) {
            NonAWSS3ItemStorage nonAWSS3ItemStorage = (NonAWSS3ItemStorage) storage;
            this.s3Config = new S3Config(
                    nonAWSS3ItemStorage.lookupCredentials(),
                    nonAWSS3ItemStorage.getRegion(),
                    nonAWSS3ItemStorage.getEndpoint(),
                    nonAWSS3ItemStorage.getBucketName()
            );
        } else {
            throw new NotImplementedException(storage.getClass().getSimpleName() + " is not supported yet!");
        }
    }

    protected CacheItemRepository cacheItemRepository() {
        if (cacheItemRepository == null) {
            synchronized (this) {
                cacheItemRepository = new CacheItemRepository(s3Config);
            }
        }

        return cacheItemRepository;
    }

    public static class ResultBuilder {

        private Result result = new Result();

        /**
         * Creates a new Result object.
         */
        public Result build() {
            Result build = new Result();
            build.infos = new ArrayList<>(result.infos);
            return build;
        }

        /**
         * Adds a given info message to the result.
         */
        public ResultBuilder withInfo(String s) {
            result.addInfo(s);
            return this;
        }
    }

    protected String performanceString(String key, long startNanoTime) {
        double duration = (System.nanoTime() - startNanoTime) / 1000000000D;
        long size = cacheItemRepository().getContentLength(key);
        long speed = (long) (size / duration);
        return String.format("%s bytes in %.2f secs (%s bytes/sec)", size, duration, speed);
    }

    /**
     * Result object.
     */
    public static class Result implements Serializable {
        private static final long serialVersionUID = 1L;

        private List<String> infos = new ArrayList<>();

        /**
         * Adds a given info message to the result.
         */
        public void addInfo(String s) {
            infos.add(s);
        }

        /**
         * Prints out all the info messages to the given logger.
         */
        public void printInfos(PrintStream logger) {
            infos.forEach(logger::println);
        }
    }

}
