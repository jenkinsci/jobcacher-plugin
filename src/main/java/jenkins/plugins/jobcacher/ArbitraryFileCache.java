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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.ObjectPath;
import jenkins.plugins.jobcacher.arbitrary.*;
import jenkins.plugins.jobcacher.arbitrary.WorkspaceHelper.TempFile;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.*;

import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * This class implements a Cache where the user can configure a path on the executor that will be cached.  Users can
 * reference environment variables on the executor in the path and supply an includes and excludes pattern to limit the
 * files that are cached.
 *
 * @author Peter Hayes
 */
public class ArbitraryFileCache extends Cache {

    private static final long serialVersionUID = 1L;

    private static final String CACHE_VALIDITY_DECIDING_FILE_HASH_FILE_EXTENSION = ".hash";

    private String path;
    private String includes;
    private String excludes;
    private boolean useDefaultExcludes = true;
    private String cacheValidityDecidingFile;
    private CompressionMethod compressionMethod = CompressionMethod.NONE;

    @DataBoundConstructor
    public ArbitraryFileCache(String path, String includes, String excludes) {
        this.path = path;
        this.includes = StringUtils.isNotBlank(includes) ? includes : "**/*";
        this.excludes = excludes;
    }

    @DataBoundSetter
    public void setUseDefaultExcludes(boolean useDefaultExcludes) {
        this.useDefaultExcludes = useDefaultExcludes;
    }

    public boolean getUseDefaultExcludes() {
        return useDefaultExcludes;
    }

    @DataBoundSetter
    public void setCompressionMethod(CompressionMethod compressionMethod) {
        this.compressionMethod = compressionMethod;
    }

    public CompressionMethod getCompressionMethod() {
        return compressionMethod;
    }

    @DataBoundSetter
    public void setCacheValidityDecidingFile(String cacheValidityDecidingFile) {
        this.cacheValidityDecidingFile = cacheValidityDecidingFile;
    }

    public String getCacheValidityDecidingFile() {
        return cacheValidityDecidingFile;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setIncludes(String includes) {
        this.includes = includes;
    }

    public String getIncludes() {
        return includes;
    }

    public void setExcludes(String excludes) {
        this.excludes = excludes;
    }

    public String getExcludes() {
        return excludes;
    }

    private String getCacheName() {
        return compressionMethod.getCacheStrategy().createCacheName(getCacheBaseName());
    }

    private String getSkipCacheTriggerFileHashFileName() {
        return getCacheBaseName() + CACHE_VALIDITY_DECIDING_FILE_HASH_FILE_EXTENSION;
    }

    public String getCacheBaseName() {
        return deriveCachePath(path);
    }

    @Override
    public String getTitle() {
        return jenkins.plugins.jobcacher.Messages.ArbitraryFileCache_displayName();
    }

    @Override
    public Saver cache(ObjectPath cachesRoot, ObjectPath fallbackCachesRoot, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        FilePath resolvedPath = resolvePath(workspace, initialEnvironment);

        ObjectPath cache = resolveValidCache(cachesRoot, fallbackCachesRoot, workspace, listener);
        if (cache == null) {
            logMessage("Skip restoring cache as no up-to-date cache exists", listener);
            return new SaverImpl(resolvedPath);
        }

        logMessage("Restoring cache...", listener);

        try {
            compressionMethod.cacheStrategy.restore(cache, resolvedPath, workspace);
        } catch (Exception e) {
            logMessage("Failed to restore cache, cleaning up " + path + "...", listener);
            resolvedPath.deleteRecursive();
        }

        return new SaverImpl(resolvedPath);
    }

    private FilePath resolvePath(FilePath workspace, EnvVars initialEnvironment) {
        String expandedPath = initialEnvironment.expand(path);

        return workspace.child(expandedPath);
    }

    private ObjectPath resolveValidCache(ObjectPath cachesRoot, ObjectPath fallbackCachesRoot, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        ObjectPath cache = resolveValidCache(cachesRoot, workspace);
        if (cache != null) {
            logMessage("Found cache in job specific caches", listener);
            return cache;
        }

        cache = resolveValidCache(fallbackCachesRoot, workspace);
        if (cache != null) {
            logMessage("Found cache in default caches", listener);
            return cache;
        }

        return null;
    }

    private ObjectPath resolveValidCache(ObjectPath cachesRoot, FilePath workspace) throws IOException, InterruptedException {
        ObjectPath cache = resolveCache(cachesRoot);
        if (cache == null || !cache.exists()) {
            return null;
        }

        if (isCacheValidityDecidingFileConfigured() && isCacheOutdated(cachesRoot, workspace)) {
            return null;
        }

        return cache;
    }

    private ObjectPath resolveCache(ObjectPath cachesRoot) throws IOException, InterruptedException {
        if (cachesRoot == null) {
            return null;
        }

        return cachesRoot.child(getCacheName());
    }

    private boolean isCacheOutdated(ObjectPath cachesRoot, FilePath workspace) throws IOException, InterruptedException {
        ObjectPath previousClearCacheTriggerFileHash = resolvePreviousCacheValidityDecidingFileHashFile(cachesRoot);
        if (!previousClearCacheTriggerFileHash.exists()) {
            return true;
        }

        return !matchesCurrentCacheValidityDecidingFileHash(previousClearCacheTriggerFileHash, workspace);
    }

    private boolean isCacheValidityDecidingFileConfigured() {
        return StringUtils.isNotEmpty(cacheValidityDecidingFile);
    }

    private ObjectPath resolvePreviousCacheValidityDecidingFileHashFile(ObjectPath cachesRoot) throws IOException, InterruptedException {
        String skipCacheTriggerFileHashFileName = createCacheValidityDecidingFileHashFileName(getCacheBaseName());

        return cachesRoot.child(skipCacheTriggerFileHashFileName);
    }

    private String createCacheValidityDecidingFileHashFileName(String baseName) {
        return baseName + CACHE_VALIDITY_DECIDING_FILE_HASH_FILE_EXTENSION;
    }

    private boolean matchesCurrentCacheValidityDecidingFileHash(ObjectPath previousCacheValidityDecidingFileHash, FilePath workspace) throws IOException, InterruptedException {
        try (TempFile tempFile = WorkspaceHelper.createTempFile(workspace, CACHE_VALIDITY_DECIDING_FILE_HASH_FILE_EXTENSION)) {
            previousCacheValidityDecidingFileHash.copyTo(tempFile.get());
            return StringUtils.equals(tempFile.get().readToString(), getCurrentCacheValidityDecidingFileHash(workspace));
        }
    }

    private String getCurrentCacheValidityDecidingFileHash(FilePath workspace) throws IOException, InterruptedException {
        FilePath fileToHash = workspace.child(cacheValidityDecidingFile);
        if (!fileToHash.exists()) {
            throw new IllegalStateException("path " + cacheValidityDecidingFile + " cannot be resolved within the current workspace");
        }

        return fileToHash.digest();
    }

    private class SaverImpl extends Saver {

        private static final long serialVersionUID = 1L;

        private final FilePath resolvedPath;

        public SaverImpl(FilePath resolvedPath) {
            this.resolvedPath = resolvedPath;
        }

        @Override
        public long calculateSize(ObjectPath objectPath, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            return resolvedPath.act(new DirectorySize(includes, excludes));
        }

        @Override
        public void save(ObjectPath cachesRoot, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            if (!resolvedPath.exists()) {
                logMessage("Cannot create cache as the path does not exist", listener);
                return;
            }

            if (isCacheValidityDecidingFileConfigured() && !isCacheOutdated(cachesRoot, workspace)) {
                logMessage("Skip cache creation as the cache is up-to-date", listener);
                return;
            }

            ObjectPath cache = resolveCache(cachesRoot);

            logMessage("Creating cache...", listener);
            compressionMethod.getCacheStrategy().cache(resolvedPath, includes, excludes, useDefaultExcludes, cache, workspace);
            if (isCacheValidityDecidingFileConfigured()) {
                updateSkipCacheTriggerFileHash(cachesRoot, workspace);
            }
        }

        private void updateSkipCacheTriggerFileHash(ObjectPath cachesRoot, FilePath workspace) throws IOException, InterruptedException {
            try (TempFile tempFile = WorkspaceHelper.createTempFile(workspace, CACHE_VALIDITY_DECIDING_FILE_HASH_FILE_EXTENSION)) {
                tempFile.get().write(getCurrentCacheValidityDecidingFileHash(workspace), StandardCharsets.UTF_8.displayName());

                ObjectPath skipCacheTriggerFileHashFile = cachesRoot.child(getSkipCacheTriggerFileHashFileName());
                skipCacheTriggerFileHashFile.copyFrom(tempFile.get());
            }
        }
    }

    private void logMessage(String message, TaskListener listener) {
        listener.getLogger().println("[Cache for " + path + "] " + message);
    }

    public HttpResponse doDynamic(StaplerRequest req, StaplerResponse rsp, @AncestorInPath Job<?, ?> job) throws IOException, ServletException, InterruptedException {
        ObjectPath cache = CacheManager.getCachePath(GlobalItemStorage.get().getStorage(), job).child(deriveCachePath(path));

        if (!cache.exists()) {
            req.getView(this, "noCache.jelly").forward(req, rsp);
            return null;
        } else {
            return cache.browse(req, rsp, job, path);
        }
    }

    @Extension
    public static final class DescriptorImpl extends CacheDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ArbitraryFileCache_displayName();
        }

        @Restricted(NoExternalUse.class)
        public ListBoxModel doFillCompressionMethodItems() {
            ListBoxModel items = new ListBoxModel();
            for (CompressionMethod method : CompressionMethod.values()) {
                items.add(method.name());
            }

            return items;
        }
    }

    public enum CompressionMethod {

        NONE(new SimpleArbitraryFileCacheStrategy()),
        ZIP(new ZipArbitraryFileCacheStrategy()),
        TARGZ(new TarArbitraryFileCacheStrategy("gz", ".tgz")),
        TAR_ZSTD(new TarArbitraryFileCacheStrategy("zstd", ".tar.zst"));

        private final ArbitraryFileCacheStrategy cacheStrategy;

        CompressionMethod(ArbitraryFileCacheStrategy cacheStrategy) {
            this.cacheStrategy = cacheStrategy;
        }

        public ArbitraryFileCacheStrategy getCacheStrategy() {
            return cacheStrategy;
        }
    }
}
