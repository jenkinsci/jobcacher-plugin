package jenkins.plugins.jobcacher.arbitrary;

import hudson.FilePath;
import jenkins.plugins.itemstorage.ObjectPath;

import java.io.IOException;

public class SimpleArbitraryFileCacheStrategy implements ArbitraryFileCacheStrategy {

    @Override
    public String createCacheName(String basename) {
        return basename;
    }

    @Override
    public void cache(FilePath source, String includes, String excludes, boolean useDefaultExcludes, ObjectPath target, FilePath workspace) throws IOException, InterruptedException {
        target.copyRecursiveFrom(includes, excludes, useDefaultExcludes, source);
    }

    @Override
    public void restore(ObjectPath source, FilePath target, FilePath workspace) throws IOException, InterruptedException {
        target.mkdirs();
        source.copyRecursiveTo(target);
    }
}
