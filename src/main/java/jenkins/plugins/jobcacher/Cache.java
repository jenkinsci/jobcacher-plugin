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

import hudson.*;
import hudson.model.Describable;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;
import jenkins.agents.ControllerToAgentFileCallable;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.ObjectPath;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * This class provides the Cache extension point that when implemented provides the caching logic for saving files
 * from the executor to the cache storage system and sending them back to the executor.
 * <p>
 * Note, that Cache is Serializable and all subclasses must conform as well to work with Pipeline plugin
 *
 * @author Peter Hayes
 */
public abstract class Cache implements Describable<Cache>, ExtensionPoint, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The permission required to delete a cache through the user interface. Deleting a cache does not destroy any
     * information that cannot be recreated by another build, so it is granted to everyone who is allowed to start a
     * build - those users are able to overwrite the cache with a new build anyway.
     */
    public static final Permission DELETE_PERMISSION = Item.BUILD;

    /**
     * Seeds the cache on the executor from the cache storage system.
     *
     * @param cache              The root of the object cache
     * @param defaultCache       The root of the alternate default object cache
     * @param build              The build in progress
     * @param workspace          The executor workspace
     * @param launcher           The launcher
     * @param listener           The task listener
     * @param initialEnvironment The initial environment variables
     * @param skipRestore        Whether to skip restoring the cache
     * @throws IOException          If an error occurs connecting to the potentially remote executor
     * @throws InterruptedException If interrupted
     */
    public abstract Saver cache(
            ObjectPath cache,
            ObjectPath defaultCache,
            Run<?, ?> build,
            FilePath workspace,
            Launcher launcher,
            TaskListener listener,
            EnvVars initialEnvironment,
            boolean skipRestore)
            throws IOException, InterruptedException;

    /**
     * Class that is used to save the cache on the remote system back to the cache storage system.
     */
    public abstract static class Saver implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * Calculates the size of the cache on the executor. It will be used to determine if the total size of the cache
         * if returned to the cache storage system would be greater than the configured maximum cache size.
         *
         * @param cache     The root of the cache
         * @param build     The build in progress
         * @param workspace The executor workspace
         * @param launcher  The launcher
         * @param listener  The task listener
         * @return The size in bytes of the remote cache
         * @throws IOException          If an error occurs connecting to the potentially remote executor
         * @throws InterruptedException If interrupted
         */
        public abstract long calculateSize(
                ObjectPath cache, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
                throws IOException, InterruptedException;

        /**
         * Saves the files from the executor to the cache storage system.
         *
         * @param cache        The root of the cache where savers should store their cache within
         * @param defaultCache The root of the alternate default object cache
         * @param build        The build in progress
         * @param workspace    The executor workspace
         * @param launcher     The launcher
         * @param listener     The task listener
         * @throws IOException          If an error occurs connecting to the potentially remote executor
         * @throws InterruptedException If interrupted
         */
        public abstract void save(
                ObjectPath cache,
                ObjectPath defaultCache,
                Run<?, ?> build,
                FilePath workspace,
                Launcher launcher,
                TaskListener listener)
                throws IOException, InterruptedException;
    }

    /**
     * Gets the human-readable title for this cache to be shown on the user interface.
     *
     * @return The title of the cache
     */
    public abstract String getTitle();

    /**
     * Gets the ancestor job when invoked via the stapler context.
     *
     * @return the job
     */
    public Job<?, ?> getJob() {
        StaplerRequest2 request = Stapler.getCurrentRequest2();

        return request == null ? null : request.findAncestorObject(Job.class);
    }

    /**
     * Deletes the cache of this configuration from the cache storage system. Implementations are expected to delete
     * everything they have created within the given caches root, but nothing else.
     * <p>
     * The default implementation does nothing, so caches that do not support manual deletion keep working.
     *
     * @param cachesRoot The root of the object cache
     * @return true if something has been deleted, false if there was nothing to delete
     * @throws IOException          If an error occurs while deleting the cache
     * @throws InterruptedException If interrupted
     */
    public boolean delete(ObjectPath cachesRoot) throws IOException, InterruptedException {
        return false;
    }

    /**
     * Checks whether the current user is allowed to delete the cache of the ancestor job. Used by the user interface
     * to decide whether the deletion controls are rendered.
     *
     * @return true if so, false otherwise
     */
    public boolean isDeletable() {
        Job<?, ?> job = getJob();

        return job != null && job.hasPermission(DELETE_PERMISSION);
    }

    /**
     * Deletes the cache of this configuration for the ancestor job and redirects back to the cache overview.
     */
    @RequirePOST
    public HttpResponse doDelete(@AncestorInPath Job<?, ?> job) throws IOException, InterruptedException {
        if (job == null) {
            return HttpResponses.notFound();
        }

        job.checkPermission(DELETE_PERMISSION);

        CacheManager.delete(GlobalItemStorage.get().getStorage(), job, this);

        return HttpResponses.redirectViaContextPath(job.getUrl() + "cache/");
    }

    /**
     * Generates a path within the cache dir given a relative or absolute path that is being cached.
     *
     * @param path The relative or absolute path that is being cached
     * @return A filepath where to save and read from the cache
     */
    public static String deriveCachePath(String path) {
        return Util.getDigestOf(path);
    }

    /**
     * Utility class to calculate the size of a potentially remote directory given a pattern and excludes.
     */
    public static class DirectorySize implements ControllerToAgentFileCallable<Long> {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String glob;
        private final String excludes;

        public DirectorySize(String glob, String excludes) {
            this.glob = glob;
            this.excludes = excludes;
        }

        @Override
        public Long invoke(File f, VirtualChannel channel) throws IOException {
            AtomicLong total = new AtomicLong(0L);

            new DirScanner.Glob(glob, excludes).scan(f, new FileVisitor() {
                @Override
                public void visit(File f, String relativePath) throws IOException {
                    if (f.isFile()) {
                        total.addAndGet(f.length());
                    }
                }

                @Override
                public boolean understandsSymlink() {
                    return true;
                }

                @Override
                public void visitSymlink(File link, String target, String relativePath) throws IOException {
                    // Skip symlinks - they should not contribute to the cache size
                }
            });

            return total.get();
        }
    }
}
