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

package jenkins.plugins.itemstorage.s3;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Based on same named class in S3 Jenkins Plugin
 *
 * Reusable class for interacting with S3 for file operations
 *
 * @author Peter Hayes
 */
public class S3Profile {
    private final ClientHelper helper;
    private final int maxRetries;
    private final long retryTime;

    @DataBoundConstructor
    public S3Profile(AmazonWebServicesCredentials credentials, String endpoint, String signerVersion, boolean pathStyleAccess, boolean parallelDownloads, Integer maxRetries, Long retryTime) {
        this.helper = new ClientHelper(credentials != null ? credentials.getCredentials() : null, endpoint, null, getProxy(), signerVersion, pathStyleAccess, parallelDownloads);
        this.maxRetries = maxRetries != null ? maxRetries : 5;
        this.retryTime = retryTime != null ? retryTime : 5L;
    }

    public int upload(final String bucketName,
                      final String path,
                      final String fileMask,
                      final String excludes,
                      boolean useDefaultExcludes,
                      final FilePath source,
                      final Map<String, String> userMetadata,
                      final String storageClass,
                      final boolean useServerSideEncryption) throws IOException, InterruptedException {
        FilePath.FileCallable<Integer> upload = new S3UploadAllCallable(
                helper,
                fileMask,
                excludes,
                useDefaultExcludes,
                bucketName,
                path,
                userMetadata,
                storageClass,
                useServerSideEncryption);

        return source.act(upload);
    }

    public boolean exists(String bucketName, String path) {
        ObjectListing objectListing = helper.client().listObjects(new ListObjectsRequest(bucketName, path, null, null, 1));

        return !objectListing.getObjectSummaries().isEmpty();
    }

    public int download(String bucketName, String pathPrefix, String fileMask, String excludes, boolean useDefaultExcludes, FilePath target) throws IOException, InterruptedException {
        FilePath.FileCallable<Integer> download = new S3DownloadAllCallable(helper, fileMask, excludes, useDefaultExcludes, bucketName, pathPrefix);

        return target.act(download);
    }

    public void delete(String bucketName, String pathPrefix) {
        ObjectListing listing = null;
        do {
            listing = listing == null ? helper.client().listObjects(bucketName, pathPrefix) : helper.client().listNextBatchOfObjects(listing);

            DeleteObjectsRequest req = new DeleteObjectsRequest(bucketName);

            List<DeleteObjectsRequest.KeyVersion> keys = new ArrayList<>(listing.getObjectSummaries().size());
            for (S3ObjectSummary summary : listing.getObjectSummaries()) {
                keys.add(new DeleteObjectsRequest.KeyVersion(summary.getKey()));
            }
            req.withKeys(keys);

            helper.client().deleteObjects(req);
        } while (listing.isTruncated());
    }

    public void rename(String bucketName, String currentPathPrefix, String newPathPrefix) {

        ObjectListing listing = null;
        do {
            listing = listing == null ? helper.client().listObjects(bucketName, currentPathPrefix) : helper.client().listNextBatchOfObjects(listing);
            for (S3ObjectSummary summary : listing.getObjectSummaries()) {
                String key = summary.getKey();

                helper.client().copyObject(bucketName, key, bucketName, newPathPrefix + key.substring(currentPathPrefix.length()));
                helper.client().deleteObject(bucketName, key);
            }
        } while (listing.isTruncated());
    }

    private ProxyConfiguration getProxy() {
        return Jenkins.getActiveInstance().proxy;
    }
}
