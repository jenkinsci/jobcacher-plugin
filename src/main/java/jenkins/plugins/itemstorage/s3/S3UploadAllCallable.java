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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.TransferManager;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by hayep on 12/2/2016.
 */
public class S3UploadAllCallable extends S3BaseUploadCallable<Integer> {
    private static final long serialVersionUID = 1L;
    private String bucketName;
    private String pathPrefix;
    private final DirScanner.Glob scanner;


    public S3UploadAllCallable(ClientHelper clientHelper, String fileMask, String excludes, boolean useDefaultExcludes, String bucketName, String pathPrefix, Map<String, String> userMetadata, String storageClass, boolean useServerSideEncryption) {
        super(clientHelper, userMetadata, storageClass, useServerSideEncryption);
        this.bucketName = bucketName;
        this.pathPrefix = pathPrefix;

        scanner = new DirScanner.Glob(fileMask, excludes, useDefaultExcludes);
    }

    /**
     * Upload from slave
     */
    @Override
    public Integer invoke(final TransferManager transferManager, File base, VirtualChannel channel) throws IOException, InterruptedException {
        if(!base.exists())  return 0;

        final AtomicInteger count = new AtomicInteger(0);
        final Uploads uploads = new Uploads();

        final Map<String, S3ObjectSummary> summaries = lookupExistingCacheEntries(transferManager.getAmazonS3Client());

        // Find files to upload that match scan
        scanner.scan(base, new FileVisitor() {
            @Override
            public void visit(File f, String relativePath) throws IOException {
                if (f.isFile()) {
                    String key = pathPrefix + "/" + relativePath;

                    S3ObjectSummary summary = summaries.get(key);
                    if (summary == null || f.lastModified() > summary.getLastModified().getTime()) {
                        final ObjectMetadata metadata = buildMetadata(f);

                        uploads.startUploading(transferManager, f, IOUtils.toBufferedInputStream(FileUtils.openInputStream(f)), new Destination(bucketName, key), metadata);

                        if (uploads.count() > 20) {
                            waitForUploads(count, uploads);
                        }
                    }
                }
            }
        });

        // Wait for each file to complete before returning
        waitForUploads(count, uploads);

        return uploads.count();
    }

    private Map<String,S3ObjectSummary> lookupExistingCacheEntries(AmazonS3 s3) {
        Map<String,S3ObjectSummary> summaries = new HashMap<>();

        ObjectListing listing = s3.listObjects(bucketName, pathPrefix);
        do {
            for (S3ObjectSummary summary : listing.getObjectSummaries()) {
                summaries.put(summary.getKey(), summary);
            }
            listing = listing.isTruncated() ? s3.listNextBatchOfObjects(listing) : null;
        } while (listing != null);

        return summaries;
    }

    private void waitForUploads(AtomicInteger count, Uploads uploads) {
        count.addAndGet(uploads.count());

        try {
            uploads.finishUploading();
        } catch (InterruptedException ie) {
            // clean up and bomb out
            uploads.cleanup();
            Thread.interrupted();
        }
    }
}