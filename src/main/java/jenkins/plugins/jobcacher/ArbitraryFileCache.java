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

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.*;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.ObjectPath;
import jenkins.plugins.jobcacher.arbitrary.*;
import jenkins.plugins.jobcacher.arbitrary.WorkspaceHelper.TempFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.*;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

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
    private static final String CACHE_VALIDITY_DECIDING_FILE_DIGEST_ALGORITHM = "MD5";
    private static final String CACHE_FILENAME_PART_SEP = "-";

    private String path;
    private String includes;
    private String excludes;
    private boolean useDefaultExcludes = true;
    private String cacheValidityDecidingFile;
    private CompressionMethod compressionMethod = CompressionMethod.TARGZ;
    private String cacheName;

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

    public String getCacheName() {
        return cacheName;
    }

    @DataBoundSetter
    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    private String getSkipCacheTriggerFileHashFileName() {
        return createCacheBaseName() + CACHE_VALIDITY_DECIDING_FILE_HASH_FILE_EXTENSION;
    }

    public String createCacheBaseName() {
        String generatedCacheName = deriveCachePath(path);
        if (StringUtils.isEmpty(this.cacheName)) {
            return generatedCacheName;
        }

        return generatedCacheName + CACHE_FILENAME_PART_SEP + this.cacheName;
    }

    @Override
    public String getTitle() {
        return jenkins.plugins.jobcacher.Messages.ArbitraryFileCache_displayName();
    }

    @Override
    public Saver cache(ObjectPath cachesRoot, ObjectPath fallbackCachesRoot, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        FilePath resolvedPath = resolvePath(workspace, initialEnvironment);

        ExistingCache existingCache = resolveExistingValidCache(cachesRoot, fallbackCachesRoot, workspace, listener);
        if (existingCache == null) {
            logMessage("Skip restoring cache as no up-to-date cache exists", listener);
            return new SaverImpl(resolvedPath);
        }

        logMessage("Restoring cache...", listener);
        long cacheRestorationStartTime = System.nanoTime();

        try {
            existingCache.restore(resolvedPath, workspace);
        } catch (Exception e) {
            logMessage("Failed to restore cache, cleaning up " + path + "...", listener);
            resolvedPath.deleteRecursive();
        }
        long cacheRestorationEndTime = System.nanoTime();
        logMessage("Cache restored in " + Duration.ofNanos(cacheRestorationEndTime - cacheRestorationStartTime).toMillis() + "ms", listener);

        return new SaverImpl(resolvedPath);
    }

    private FilePath resolvePath(FilePath workspace, EnvVars initialEnvironment) {
        String expandedPath = initialEnvironment.expand(path);

        return workspace.child(expandedPath);
    }

    private ExistingCache resolveExistingValidCache(ObjectPath cachesRoot, ObjectPath fallbackCachesRoot, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        logMessage("Searching cache in job specific caches...", listener);
        ExistingCache cache = resolveExistingValidCache(cachesRoot, workspace, listener);
        if (cache != null) {
            logMessage("Found cache in job specific caches", listener);
            return cache;
        }

        logMessage("Searching cache in default caches...", listener);
        cache = resolveExistingValidCache(fallbackCachesRoot, workspace, listener);
        if (cache != null) {
            logMessage("Found cache in default caches", listener);
            return cache;
        }

        return null;
    }

    private ExistingCache resolveExistingValidCache(ObjectPath cachesRoot, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        ExistingCache existingCache = resolveExistingCache(cachesRoot);
        if (existingCache == null) {
            return null;
        }

        if (!isCacheValidityDecidingFileConfigured()) {
            return existingCache;
        }

        if (!isOneCacheValidityDecidingFilePresent(workspace)) {
            logMessage("cacheValidityDecidingFile configured, but file(s) not present in workspace - considering cache anyway", listener);
            return existingCache;
        }

        return isCacheOutdated(cachesRoot, workspace, listener) ? null : existingCache;
    }

    private ExistingCache resolveExistingCache(ObjectPath cachesRoot) throws IOException, InterruptedException {
        if (cachesRoot == null) {
            return null;
        }

        for (CompressionMethod compressionMethod : CompressionMethod.values()) {
            ObjectPath cache = resolveCachePathForCompressionMethod(cachesRoot, compressionMethod);
            if (cache.exists()) {
                return new ExistingCache(cache, compressionMethod);
            }
        }

        return null;
    }

    private ObjectPath resolveCachePathForCompressionMethod(ObjectPath cachesRoot, CompressionMethod compressionMethod) throws IOException, InterruptedException {
        return cachesRoot.child(compressionMethod.getCacheStrategy().createCacheName(createCacheBaseName()));
    }

    private boolean isCacheOutdated(ObjectPath cachesRoot, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        ObjectPath previousClearCacheTriggerFileHash = resolvePreviousCacheValidityDecidingFileHashFile(cachesRoot);
        if (!previousClearCacheTriggerFileHash.exists()) {
            logMessage("cacheValidityDecidingFile configured, but previous hash not available - cache outdated", listener);
            return true;
        }

        if (!matchesCurrentCacheValidityDecidingFileHash(previousClearCacheTriggerFileHash, workspace)) {
            logMessage("cacheValidityDecidingFile configured, but previous hash does not match - cache outdated", listener);
            return true;
        }

        return false;
    }

    private boolean isCacheValidityDecidingFileConfigured() {
        return StringUtils.isNotEmpty(cacheValidityDecidingFile);
    }

    private ObjectPath resolvePreviousCacheValidityDecidingFileHashFile(ObjectPath cachesRoot) throws IOException, InterruptedException {
        String skipCacheTriggerFileHashFileName = createCacheValidityDecidingFileHashFileName(createCacheBaseName());

        return cachesRoot.child(skipCacheTriggerFileHashFileName);
    }

    private String createCacheValidityDecidingFileHashFileName(String baseName) {
        return baseName + CACHE_VALIDITY_DECIDING_FILE_HASH_FILE_EXTENSION;
    }

    private boolean matchesCurrentCacheValidityDecidingFileHash(ObjectPath previousCacheValidityDecidingFileHashFile, FilePath workspace) throws IOException, InterruptedException {
        if (!isOneCacheValidityDecidingFilePresent(workspace)) {
            return false;
        }

        try (TempFile tempFile = WorkspaceHelper.createTempFile(workspace, CACHE_VALIDITY_DECIDING_FILE_HASH_FILE_EXTENSION)) {
            previousCacheValidityDecidingFileHashFile.copyTo(tempFile.get());

            try (InputStream inputStream = tempFile.get().read()) {
                String previousCacheValidityDecidingFileHash = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                String currentCacheValidityDecidingFileHash = getCurrentCacheValidityDecidingFileHash(workspace);

                return StringUtils.equals(previousCacheValidityDecidingFileHash, currentCacheValidityDecidingFileHash);
            }
        }
    }

    private String getCurrentCacheValidityDecidingFileHash(FilePath workspace) throws IOException, InterruptedException {
        if (!isOneCacheValidityDecidingFilePresent(workspace)) {
            throw new IllegalStateException("path " + cacheValidityDecidingFile + " cannot be resolved within the current workspace");
        }

        try {
            MessageDigest messageDigest = MessageDigest.getInstance(CACHE_VALIDITY_DECIDING_FILE_DIGEST_ALGORITHM);
            for (FilePath cacheValidityDecidingFile : resolveCacheValidityDecidingFiles(workspace)) {
                try (InputStream inputStream = cacheValidityDecidingFile.read()) {
                    DigestInputStream digestInputStream = new DigestInputStream(inputStream, messageDigest);
                    IOUtils.copy(digestInputStream, NullOutputStream.NULL_OUTPUT_STREAM);
                }
            }

            return Util.toHexString(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private boolean isOneCacheValidityDecidingFilePresent(FilePath workspace) throws IOException, InterruptedException {
        return resolveCacheValidityDecidingFiles(workspace).length > 0;
    }

    private FilePath[] resolveCacheValidityDecidingFiles(FilePath workspace) throws IOException, InterruptedException {
        return workspace.list(cacheValidityDecidingFile);
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

            if (isCacheValidityDecidingFileConfigured()) {
                ExistingCache existingValidCache = resolveExistingValidCache(cachesRoot, workspace, listener);
                if (existingValidCache != null) {
                    logMessage("Skip cache creation as the cache is up-to-date", listener);
                    return;
                }
            }

            ExistingCache existingCache = resolveExistingCache(cachesRoot);
            if (existingCache != null && existingCache.getCompressionMethod() != compressionMethod) {
                logMessage("Delete existing cache as the compression method has been changed", listener);
                existingCache.getCache().deleteRecursive();
            }

            ObjectPath cache = resolveCachePathForCompressionMethod(cachesRoot, compressionMethod);

            logMessage("Creating cache...", listener);
            long cacheCreationStartTime = System.nanoTime();
            compressionMethod.getCacheStrategy().cache(resolvedPath, includes, excludes, useDefaultExcludes, cache, workspace);
            if (isCacheValidityDecidingFileConfigured() && isOneCacheValidityDecidingFilePresent(workspace)) {
                updateSkipCacheTriggerFileHash(cachesRoot, workspace);
            }
            long cacheCreationEndTime = System.nanoTime();
            logMessage("Cache created in " + Duration.ofNanos(cacheCreationEndTime - cacheCreationStartTime).toMillis() + "ms", listener);
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
        String cacheIdentifier = path;
        if (getCacheName() != null) {
            cacheIdentifier += " (" + getCacheName() + ")";
        }

        listener.getLogger().println("[Cache for " + cacheIdentifier + " with id " + deriveCachePath(path) + "] " + message);
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
    @Symbol("arbitraryFileCache")
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
        TARGZ(new TarArbitraryFileCacheStrategy(
                GzipCompressorOutputStream::new,
                GzipCompressorInputStream::new,
                ".tgz")
        ),
        TAR_ZSTD(new TarArbitraryFileCacheStrategy(
                out -> {
                    ZstdOutputStream outputStream = new ZstdOutputStream(out);
                    outputStream.setWorkers(0); // use all available cores
                    return outputStream;
                },
                ZstdInputStream::new,
                ".tar.zst")
        );

        private final ArbitraryFileCacheStrategy cacheStrategy;

        CompressionMethod(ArbitraryFileCacheStrategy cacheStrategy) {
            this.cacheStrategy = cacheStrategy;
        }

        public ArbitraryFileCacheStrategy getCacheStrategy() {
            return cacheStrategy;
        }
    }

    private static class ExistingCache {

        private final ObjectPath cache;
        private final CompressionMethod compressionMethod;

        private ExistingCache(ObjectPath cache, CompressionMethod compressionMethod) {
            this.cache = cache;
            this.compressionMethod = compressionMethod;
        }

        public ObjectPath getCache() {
            return cache;
        }

        public CompressionMethod getCompressionMethod() {
            return compressionMethod;
        }

        public void restore(FilePath target, FilePath workspace) throws IOException, InterruptedException {
            compressionMethod.getCacheStrategy().restore(cache, target, workspace);
        }

    }

}
