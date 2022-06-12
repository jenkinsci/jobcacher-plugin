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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * From the S3 Jenkins plugin modified a bit to meet this use case
 */
public final class Downloads {

    private static final Logger LOGGER = Logger.getLogger(Downloads.class.getName());

    private final List<Memo> startedDownloads = new LinkedList<>();

    public Downloads() {
    }

    public void startDownload(TransferManager manager, File base, String pathPrefix, S3ObjectSummary summary) throws AmazonServiceException, IOException {
        // calculate target file name
        File targetFile = FileUtils.getFile(base, summary.getKey().substring(pathPrefix.length() + 1));

        // if target file exists, only download it if newer
        if (targetFile.lastModified() < summary.getLastModified().getTime()) {
            // ensure directory above file exists
            FileUtils.forceMkdir(targetFile.getParentFile());

            // Start the download
            Download download = manager.download(summary.getBucketName(), summary.getKey(), targetFile);

            // Keep for later
            startedDownloads.add(new Memo(download, targetFile, summary.getLastModified().getTime()));
        }
    }

    public void finishDownloading() throws InterruptedException {
        for (Memo memo : startedDownloads) {
            memo.download.waitForCompletion();

            if (!memo.file.setLastModified(memo.timestamp)) {
                LOGGER.warning("Could not set last modified time on " + memo.file);
            }
        }

        startedDownloads.clear();
    }

    public int count() {
        return startedDownloads.size();
    }

    private static class Memo {
        public Download download;
        public File file;
        public long timestamp;

        public Memo(Download download, File file, long timestamp) {
            this.download = download;
            this.file = file;
            this.timestamp = timestamp;
        }
    }
}
