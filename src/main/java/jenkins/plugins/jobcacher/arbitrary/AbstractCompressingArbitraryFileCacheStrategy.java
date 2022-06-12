package jenkins.plugins.jobcacher.arbitrary;

import hudson.FilePath;
import jenkins.plugins.itemstorage.ObjectPath;
import jenkins.plugins.jobcacher.arbitrary.WorkspaceHelper.TempFile;

import java.io.IOException;

public abstract class AbstractCompressingArbitraryFileCacheStrategy implements ArbitraryFileCacheStrategy {

    @Override
    public String createCacheName(String cacheBaseName) {
        return cacheBaseName + getArchiveExtension();
    }

    @Override
    public void cache(FilePath localSource, String includes, String excludes, boolean useDefaultExcludes, ObjectPath remoteTarget, FilePath workspace) throws IOException, InterruptedException {
        try (TempFile localTarget = WorkspaceHelper.createTempFile(workspace, getArchiveExtension())) {
            compress(localSource, includes, excludes, useDefaultExcludes, localTarget.get());
            remoteTarget.copyFrom(localTarget.get());
        }
    }

    @Override
    public void restore(ObjectPath remoteSource, FilePath localTarget, FilePath workspace) throws IOException, InterruptedException {
        localTarget.mkdirs();

        try (TempFile localSource = WorkspaceHelper.createTempFile(workspace, getArchiveExtension())) {
            remoteSource.copyTo(localSource.get());
            uncompress(localSource.get(), localTarget);
        }
    }

    protected abstract String getArchiveExtension();

    protected abstract void uncompress(FilePath source, FilePath target) throws IOException, InterruptedException;

    protected abstract void compress(FilePath source, String includes, String excludes, boolean useDefaultExcludes, FilePath target) throws IOException, InterruptedException;
}
