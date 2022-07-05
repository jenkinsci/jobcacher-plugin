package io.jenkins.plugins.pipeline.cache.s3;

/**
 * Represents a cache item.
 */
public class CacheItem {
    private final String key;
    private final long contentLength;
    private final long lastAccess;
    private final long lastModified;

    /**
     * @param key Unique identifier (e.g. maven-d41d8cd98f00b204e9800998ecf8427e)
     * @param contentLength Size of the cache in byte
     * @param lastAccess Unix time in ms when the cache was accessed last
     * @param lastModified Unix time in ms when the cache was modified last
     */
    public CacheItem(String key, long contentLength, long lastAccess, long lastModified) {
        this.key = key;
        this.contentLength = contentLength;
        this.lastAccess = lastAccess;
        this.lastModified = lastModified;
    }

    public String getKey() {
        return key;
    }

    public long getContentLength() {
        return contentLength;
    }

    public long getLastAccess() {
        return lastAccess;
    }

    public long getLastModified() {
        return lastModified;
    }
}
