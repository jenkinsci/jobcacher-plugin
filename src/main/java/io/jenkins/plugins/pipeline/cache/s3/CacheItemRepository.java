package io.jenkins.plugins.pipeline.cache.s3;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class CacheItemRepository {

    private static final String LAST_ACCESS = "LAST_ACCESS";

    private final AmazonS3 s3;
    private final String bucket;

    public CacheItemRepository(S3Config config) {
        this.s3 = createS3Client(config);
        this.bucket = config.getBucket();
    }

    protected AmazonS3 createS3Client(S3Config config) {
        return AmazonS3ClientBuilder
                .standard()
                .withPathStyleAccessEnabled(true)
                .withCredentials(config.getCredentials())
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(config.getEndpoint(), config.getRegion()))
                .build();
    }

    /**
     * Provides the total size of all cache items.
     */
    public long getTotalCacheSize() {
        return Stream.of(s3.listObjects(bucket))
                .flatMap(this::truncateSize)
                .reduce(Long::sum)
                .orElse(0L);
    }

    /**
     * Provides a stream of all cache items.
     */
    public Stream<CacheItem> findAll() {
        return truncateCacheItems(s3.listObjects(bucket));
    }

    /**
     * Removes items from the cache.
     * @param keys Stream of keys which should be removed
     * @return count of removed items
     */
    public int delete(Stream<String> keys) {
        return s3.deleteObjects(new DeleteObjectsRequest(bucket)
                .withKeys(keys.toArray(String[]::new))
        ).getDeletedObjects()
                .size();
    }

    /**
     * Provides the size of a cache item in byte.
     */
    public long getContentLength(String key) {
        return s3.getObjectMetadata(bucket, key).getContentLength();
    }

    /**
     * Provides the {@link S3Object} assigned to a given key or null if it not exists.
     */
    public S3Object getS3Object(String key) {
        return s3.getObject(new GetObjectRequest(bucket, key));
    }

    /**
     * Updates the last access timestamp of a given {@link S3Object}.
     */
    public void updateLastAccess(S3Object s3Object) {
        ObjectMetadata metadataCopy = s3Object.getObjectMetadata();
        updateLastAccess(metadataCopy);

        s3.copyObject(new CopyObjectRequest(bucket, s3Object.getKey(), bucket, s3Object.getKey())
                .withNewObjectMetadata(metadataCopy));
    }

    /**
     * Updates the last access timestamp of a given {@link ObjectMetadata} <b>but it will not be persisted</b>.
     */
    public static void updateLastAccess(ObjectMetadata metadata) {
        metadata.addUserMetadata(LAST_ACCESS, Long.toString(System.currentTimeMillis()));
    }

    /**
     * Finds the best matching key by a given list of restoreKeys. Note, that restoreKeys doesn't have to be necessarily existing
     * keys. They can also just be prefixes of existing keys. If there is no matching key found at all then null is returned.
     */
    public String findKeyByRestoreKeys(String... restoreKeys) {
        if (restoreKeys == null) {
            return null;
        }

        // 1. try exact match
        for (String restoreKey : restoreKeys) {
            if (exists(restoreKey)) {
                return restoreKey;
            }
        }

        // 2. try prefix match
        return Arrays.stream(restoreKeys)
                .map(this::findKeyByPrefix)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns true if the object with the given exists, otherwise false.
     */
    public boolean exists(String key) {
        return s3.doesObjectExist(bucket, key);
    }

    /**
     * Creates an {@link OutputStream} for a given key. This can be used to write data directly to an object in S3.
     */
    public OutputStream createObjectOutputStream(String key) {
        return new S3OutputStream(s3, bucket, key);
    }

    /**
     * Returns true if the bucket exists and is accessible, otherwise false.
     */
    public boolean bucketExists() {
        try {
            s3.headBucket(new HeadBucketRequest(bucket));
            return true;
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private Stream<CacheItem> truncateCacheItems(ObjectListing listing) {
        return truncate(listing, this::mapToCacheItems);
    }

    private <R> Stream<R> truncate(ObjectListing listing, Function<ObjectListing, Stream<R>> transform) {
        if (listing.isTruncated()) {
            return Stream.concat(
                    transform.apply(listing),
                    truncate(s3.listNextBatchOfObjects(listing), transform)
            );
        }
        return transform.apply(listing);
    }

    private Stream<CacheItem> mapToCacheItems(ObjectListing listing) {
        return listing.getObjectSummaries().stream()
                .map(this::mapToCacheItem);
    }

    private CacheItem mapToCacheItem(S3ObjectSummary s3ObjectSummary) {
        ObjectMetadata metadata = s3.getObjectMetadata(bucket, s3ObjectSummary.getKey());
        return new CacheItem(
                s3ObjectSummary.getKey(),
                metadata.getContentLength(),
                mapToLastAccess(metadata),
                s3ObjectSummary.getLastModified().getTime()
        );
    }

    private Stream<Long> truncateSize(ObjectListing listing) {
        return truncate(listing, this::mapToSize);
    }

    private Stream<Long> mapToSize(ObjectListing listing) {
        return listing.getObjectSummaries().stream().map(S3ObjectSummary::getSize);
    }

    private Long mapToLastAccess(ObjectMetadata objectMetadata) {
        if (!objectMetadata.getUserMetadata().containsKey(LAST_ACCESS)) {
            return 0L;
        }

        return Long.valueOf(objectMetadata.getUserMetadata().get(LAST_ACCESS));
    }

    private String findKeyByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return null;
        }

        ObjectListing listing = s3.listObjects(bucket, prefix);

        // 1. no matching key at all
        if (listing.getObjectSummaries().isEmpty()) {
            return null;
        }

        // 2. there is one key with the same prefix
        if (listing.getObjectSummaries().size() == 1) {
            return listing.getObjectSummaries().get(0).getKey();
        }

        // 3. there are more than one keys with the same prefix -> return the latest one
        return truncateCacheItems(listing)
                .sorted(Comparator.comparing(CacheItem::getLastModified).reversed())
                .map(CacheItem::getKey)
                .findFirst().orElse(null);
    }

}
