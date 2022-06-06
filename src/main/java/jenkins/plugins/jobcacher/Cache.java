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
import hudson.model.AbstractDescribableImpl;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import jenkins.MasterToSlaveFileCallable;
import jenkins.plugins.itemstorage.ObjectPath;
import org.kohsuke.stapler.Stapler;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class provides the Cache extension point that when implemented provides the caching logic for saving files
 * from the executor to the master and sending them back to the executor.
 *
 * Note, that Cache is Serializable and all subclasses must conform as well to work with Pipeline plugin
 *
 * @author Peter Hayes
 */
public abstract class Cache extends AbstractDescribableImpl<Cache> implements ExtensionPoint, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * To be implemented method that will be called to seed the cache on the executor from the master
     *
     * @param cache The root of the object cache
     * @param cache The root of the alternate default object cache
     * @param build The build in progress
     * @param workspace The executor workspace
     * @param launcher The launcher
     * @param listener The task listener
     * @param initialEnvironment The initial environment variables
     * @throws IOException If an error occurs connecting to the potentially remote executor
     * @throws InterruptedException If interrupted
     */
    public abstract Saver cache(ObjectPath cache, ObjectPath defaultCache, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException;

    /**
     * This method recursively copies files from the sourceDir to the path on the executor
     *
     * @param source             The source directory of the cache
     * @param workspace          The executor workspace that the destination path will be referenced
     * @param listener           The task listener
     * @param path               The path on the executor to store the source cache on
     * @param includes           The glob expression that will filter the contents of the path
     * @param excludes           The excludes expression that will filter contents of the path
     * @param useDefaultExcludes Whether to use default excludes additionally to the manually specified excludes
     * @throws IOException          If an error occurs connecting to the potentially remote executor
     * @throws InterruptedException If interrupted
     */
    protected void cachePath(ObjectPath source, ObjectPath defaultSource, FilePath workspace, TaskListener listener, String path, String includes, String excludes, boolean useDefaultExcludes) throws IOException, InterruptedException {

        if (source.exists() || (defaultSource != null && defaultSource.exists())) {
            FilePath targetDirectory = workspace.child(path);

            if (!targetDirectory.exists()) {
                targetDirectory.mkdirs();
            }

            if (source.exists()) {
                listener.getLogger().println("Caching " + path + " to executor");
                source.copyRecursiveTo(includes, excludes, useDefaultExcludes, targetDirectory);
            } else {
                listener.getLogger().println("Caching " + path + " to executor using default cache");
                defaultSource.copyRecursiveTo(includes, excludes, useDefaultExcludes, targetDirectory);
            }
        } else {
            listener.getLogger().println("Skip caching as no cache exists for " + path);
        }
    }

    /**
     * Class that is used to save the cache on the remote system back to the master.  This class must be able to be
     * Serialized
     */
    public static abstract class Saver implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Calculate the size of the cache on the executor which will be used to determine if the total size of the cache
         * if returned to the master would be greater than the configured maxiumum cache size.
         *
         *
         * @param cache The root of the cache
         * @param build The build in progress
         * @param workspace The executor workspace
         * @param launcher The launcher
         * @param listener The task listener
         * @return The size in bytes of the remote cache
         * @throws IOException If an error occurs connecting to the potentially remote executor
         * @throws InterruptedException If interrupted
         */
        public abstract long calculateSize(ObjectPath cache, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException;

        /**
         * To be implemented method that will be called to save the files from the executor to the master
         *
         * @param cache The root of the cache where savers should store their cache within
         * @param build The build in progress
         * @param workspace The executor workspace
         * @param launcher The launcher
         * @param listener The task listener
         * @throws IOException If an error occurs connecting to the potentially remote executor
         * @throws InterruptedException If interrupted
         */
        public abstract void save(ObjectPath cache, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException;

        /**
         * This method recursively copies files from the path on the executor to the master target directory
         *
         * @param target             The target directory of the cache
         * @param workspace          The executor workspace that the destination path will be referenced
         * @param listener           The task listener
         * @param path               The path on the executor to store the source cache on
         * @param includes           The glob expression that will filter the contents of the path
         * @param excludes           The excludes expression that will filter contents of the path
         * @param useDefaultExcludes Whether to use default excludes additionally to the manually specified excludes
         * @throws IOException          If an error occurs connecting to the potentially remote executor
         * @throws InterruptedException If interrupted
         */
        protected void savePath(ObjectPath target, FilePath workspace, TaskListener listener, String path, String includes, String excludes, boolean useDefaultExcludes) throws IOException, InterruptedException {

            FilePath source = workspace.child(path);

            listener.getLogger().println("Storing " + path + " in cache");

            target.copyRecursiveFrom(includes, excludes, useDefaultExcludes, source);
        }
    }


    /**
     * Get the human readable title for this cache to be shown on the user interface
     *
     * @return The title of the cache
     */
    public abstract String getTitle();

    /**
     * Get ancestor job when invoked via the stapler context
     * @return the job
     */
    public Job<?,?> getJob() {
        return Stapler.getCurrentRequest().findAncestorObject(Job.class);
    }

    /**
     * Generate a path within the cache dir given a relative or absolute path that is being cached
     *
     * @param path The relative or absolute path that is being cached
     * @return A filepath where to save and read from the cache
     */
    public static String deriveCachePath(String path) {
        return Util.getDigestOf(path);
    }

    /**
     * Utility class to calculate the size of a potentially remote directory given a pattern and excludes
     */
    public static class DirectorySize extends MasterToSlaveFileCallable<Long> {
        private static final long serialVersionUID = 1L;
        private final String glob;
        private final String excludes;
        public DirectorySize(String glob, String excludes) {
            this.glob = glob;
            this.excludes = excludes;
        }
        @Override public Long invoke(File f, VirtualChannel channel) throws IOException {
            final AtomicLong total = new AtomicLong(0L);

            new DirScanner.Glob(glob, excludes).scan(f, new FileVisitor() {
                @Override
                public void visit(File f, String relativePath) throws IOException {
                    if (f.isFile()) {
                        total.addAndGet(f.length());
                    }
                }
            });
            return total.get();
        }
    }
}
