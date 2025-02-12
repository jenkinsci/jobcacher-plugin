package jenkins.plugins.jobcacher;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.plugins.itemstorage.ItemStorage;
import jenkins.plugins.itemstorage.ObjectPath;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Peter Hayes
 */
public class CacheManager {

    private static final Logger LOG = Logger.getLogger(CacheManager.class.getName());

    // Could potentially grow indefinitely as jobs are created and destroyed
    private static final Map<String, Object> locks = new HashMap<>();

    public static ObjectPath getCachePath(ItemStorage<?> storage, Job<?, ?> job) {
        return storage.getObjectPath(job, "cache");
    }

    public static ObjectPath getCachePath(ItemStorage<?> storage, Run<?, ?> run) {
        return getCachePath(storage, run.getParent());
    }

    public static ObjectPath getCachePathForBranch(ItemStorage<?> storage, Run<?, ?> run, String branch) {
        return storage.getObjectPathForBranch(run.getParent(), "cache", branch);
    }

    private static Object getLock(Job<?, ?> j) {
        String jobFullName = j.getFullName();
        return locks.computeIfAbsent(jobFullName, k -> new Object());
    }

    /**
     * Internal method only
     */
    public static List<Cache.Saver> cache(ItemStorage<?> storage, Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment, List<Cache> caches, String defaultBranch, boolean skipRestore) throws IOException, InterruptedException {
        ObjectPath cachePath = getCachePath(storage, run);

        ObjectPath defaultCachePath = null;

        if (defaultBranch != null && !defaultBranch.isEmpty()) {
            defaultCachePath = getCachePathForBranch(storage, run, URLEncoder.encode(defaultBranch, StandardCharsets.UTF_8));
        }

        LOG.fine("Preparing cache for build " + run);

        // Lock the cache for reading - would be nice to make it more fine grain for multiple readers of cache
        List<Cache.Saver> cacheSavers = new ArrayList<>();
        synchronized (getLock(run.getParent())) {
            for (Cache cache : caches) {
                cacheSavers.add(cache.cache(cachePath, defaultCachePath, run, workspace, launcher, listener, initialEnvironment, skipRestore));
            }
        }
        return cacheSavers;
    }

    /**
     * Internal method only
     */
    public static void save(ItemStorage<?> storage, Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener, Long maxCacheSize, List<Cache> caches, List<Cache.Saver> cacheSavers, String defaultBranch) throws IOException, InterruptedException {
        ObjectPath cachePath = getCachePath(storage, run);

        ObjectPath defaultCachePath = null;

        if (defaultBranch != null && !defaultBranch.isEmpty()) {
            defaultCachePath = getCachePathForBranch(storage, run, URLEncoder.encode(defaultBranch, StandardCharsets.UTF_8));
        }

        // First calculate size of cache to check if it should just be deleted
        boolean exceedsMaxCacheSize = exceedsMaxCacheSize(cachePath, run, workspace, launcher, listener, maxCacheSize, cacheSavers);

        // synchronize on the build's parent object as we are going to write to the shared cache
        synchronized (getLock(run.getParent())) {
            // If total size is greater than configured maximum, delete all caches to start fresh next build
            if (exceedsMaxCacheSize) {
                listener.getLogger().println("Removing job cache as it has grown beyond configured maximum size of " +
                        maxCacheSize + "M. Next build will start with no cache.");

                if (cachePath.exists()) {
                    cachePath.deleteRecursive();
                } else {
                    listener.getLogger().println("Cache does not exist even though max cache was reached." +
                            "  You may want to consider increasing maximum cache size.");
                }
            } else {
                // Otherwise, request each cache to save itself for the next build
                LOG.fine("Saving cache for build " + run);
                for (Cache.Saver saver : cacheSavers) {
                    saver.save(cachePath, defaultCachePath, run, workspace, launcher, listener);
                }
            }
        }

        // Add a build action so that users can navigate the cache stored on the Jenkins controller through UI
        if (run.getAction(CacheBuildLastAction.class) == null) {
            run.addAction(new CacheBuildLastAction(caches));
        } else {
            run.getAction(CacheBuildLastAction.class).addCaches(caches);
        }
    }

    private static boolean exceedsMaxCacheSize(ObjectPath cachePath, Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener, Long maxCacheSize, List<Cache.Saver> cacheSavers) throws IOException, InterruptedException {
        if (maxCacheSize == null || maxCacheSize == 0) {
            return false;
        }

        long totalSize = 0L;
        for (Cache.Saver saver : cacheSavers) {
            totalSize += saver.calculateSize(cachePath, run, workspace, launcher, listener);
        }

        return totalSize > maxCacheSize * 1024 * 1024;
    }

}
