package jenkins.plugins.jobcacher.arbitrary;

import hudson.FilePath;
import java.io.IOException;
import jenkins.plugins.itemstorage.ObjectPath;

public class SimpleArbitraryFileCacheStrategy implements ArbitraryFileCacheStrategy {

    @Override
    public String createCacheName(String basename) {
        return basename;
    }

    @Override
    public void cache(
            FilePath source,
            String includes,
            String excludes,
            boolean useDefaultExcludes,
            ObjectPath target,
            FilePath workspace)
            throws IOException, InterruptedException {
        throw new UnsupportedOperationException("This compression method is not supported anymore");
    }

    @Override
    public void restore(ObjectPath source, FilePath target, FilePath workspace)
            throws IOException, InterruptedException {
        throw new UnsupportedOperationException("This compression method is not supported anymore");
    }
}
