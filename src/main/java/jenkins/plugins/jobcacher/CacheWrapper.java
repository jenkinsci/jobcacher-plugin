/*
 * The MIT License
 *
 * Copyright 2016 Peter Hayes.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.jobcacher;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by hayep on 11/26/2016.
 */
public class CacheWrapper extends SimpleBuildWrapper {
    private final static Logger LOG = Logger.getLogger(CacheWrapper.class.getName());

    private long maxCacheSize = 0L;
    private List<Cache> caches = new ArrayList<>();

    public CacheWrapper() { }

    @DataBoundConstructor
    public CacheWrapper(long maxCacheSize, List<Cache> caches) {
        this.maxCacheSize = maxCacheSize;
        this.caches = caches == null ? Collections.EMPTY_LIST : new ArrayList<>(caches);
    }

    public long getMaxCacheSize() {
        return maxCacheSize;
    }

    public void setMaxCacheSize(long maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    public List<Cache> getCaches() {
        return caches == null ? Collections.EMPTY_LIST : Collections.unmodifiableList(caches);
    }

    public void setCaches(List<Cache> caches) {
        this.caches = caches;
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        LOG.fine("Preparing cache for build " + build);

        File cacheDir = findCacheDir(build.getParent());

        // Lock the cache for reading - would be nice to make it more fine grain for multiple readers of cache
        synchronized (build.getParent()) {
            for (Cache cache : caches) {
                cache.cache(cacheDir, build, workspace, launcher, listener, initialEnvironment);
            }
        }

        context.setDisposer(new CacheDisposer(cacheDir, maxCacheSize, caches));
    }

    public static File findCacheDir(Job job) {
        return new File(job.getRootDir(), "cache");
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.CacheWrapper_DisplayName();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        public List<CacheDescriptor> getCacheDescriptors() {
            return Jenkins.getInstance().getDescriptorList(Cache.class);
        }
    }

    private static class CacheDisposer extends Disposer {
        private File cacheDir;
        private long maxCacheSize;
        private List<Cache> caches;

        @DataBoundConstructor
        public CacheDisposer(File cacheDir, long maxCacheSize, List<Cache> caches) {
            this.cacheDir = cacheDir;
            this.maxCacheSize = maxCacheSize;
            this.caches = caches;
        }

        private long getMaxCacheSizeInBytes() {
            return maxCacheSize * 1024 * 1024;
        }

        @Override
        public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {

            // First calculate size of cache to check if it should just be deleted
            long totalSize = 0L;
            for (Cache cache : caches) {
                totalSize += cache.calculateSize(build, workspace, launcher, listener);
            }

            // synchronize on the build's parent object as we are going to write to the shared cache
            synchronized (build.getParent()) {

                // If total size is greater than configured maximum, delete all caches to start fresh next build
                if (totalSize > getMaxCacheSizeInBytes()) {
                    listener.getLogger().println("Removing job cache as it has grown beyond configured maximum size of " +
                            maxCacheSize + "M. Next build will start with no cache.");

                    FilePath cachePath = new FilePath(cacheDir);
                    if (cachePath.exists()) {
                        cachePath.deleteRecursive();
                    } else {
                        listener.getLogger().println("Cache does not exist even though max cache was reached." +
                                "  You may want to consider increasing maximum cache size.");
                    }
                } else {
                    // Otherwise, request each cache to save itself for the next build
                    LOG.fine("Saving cache for build " + build);
                    for (Cache cache : caches) {
                        cache.save(cacheDir, build, workspace, launcher, listener);
                    }
                }
            }

            // Add a build action so that users can navigate the cache stored on master through UI
            build.addAction(new CacheBuildLastAction(caches));
        }
    }
}
