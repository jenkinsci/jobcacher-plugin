package jenkins.plugins.jobcacher.arbitrary;

import hudson.FilePath;
import jenkins.plugins.itemstorage.ObjectPath;

import java.io.IOException;
import java.io.Serializable;

public interface ArbitraryFileCacheStrategy extends Serializable {

    String createCacheName(String basename);

    void cache(FilePath source, String includes, String excludes, boolean useDefaultExcludes, ObjectPath target, FilePath workspace) throws IOException, InterruptedException;

    void restore(ObjectPath source, FilePath target, FilePath workspace) throws IOException, InterruptedException;
}
