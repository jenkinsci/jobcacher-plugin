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
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Based on S3 Jenkins Plugin class.
 *
 * This class tracks uploads via the transfer manager as uploads are asynchronous
 */
public class Uploads {

    private static final Logger LOGGER = Logger.getLogger(Uploads.class.getName());
    private static final int MULTIPART_UPLOAD_THRESHOLD = 16*1024*1024; // 16 MB

    private final HashMap<File, Upload> startedUploads = new HashMap<>();
    private final HashMap<File, InputStream> openedStreams = new HashMap<>();

    public Uploads() {
    }

    public void startUploading(TransferManager manager, File file, InputStream inputStream, Destination dest, ObjectMetadata metadata) throws AmazonServiceException {
        final PutObjectRequest request = new PutObjectRequest(dest.bucketName, dest.objectName, inputStream, metadata);

        // Set the buffer size (ReadLimit) equal to the multipart upload size,
        // allowing us to resend data if the connection breaks.
        request.getRequestClientOptions().setReadLimit(MULTIPART_UPLOAD_THRESHOLD);
        manager.getConfiguration().setMultipartUploadThreshold( (long) MULTIPART_UPLOAD_THRESHOLD);

        final Upload upload = manager.upload(request);
        startedUploads.put(file, upload);
        openedStreams.put(file, inputStream);
    }

    public void finishUploading() throws InterruptedException {
        for (Map.Entry<File, Upload> startedUpload : startedUploads.entrySet()) {
            finishUploading(startedUpload.getKey(), startedUpload.getValue());
        }

        startedUploads.clear();
    }

    private void finishUploading(File file, Upload upload) throws InterruptedException {
        if (upload == null) {
            LOGGER.info("File: " + file.getName() + " already was uploaded");
            return;
        }

        try {
            upload.waitForCompletion();
        } finally {
            closeStream(file, openedStreams.remove(file));
        }
    }

    public void cleanup() {
        for (Map.Entry<File, InputStream> openedStream : openedStreams.entrySet()) {
            closeStream(openedStream.getKey(), openedStream.getValue());
        }
    }

    private void closeStream(File file, InputStream inputStream) {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to close stream for file:" + file);
        }
    }

    public int count() {
        return startedUploads.size();
    }
}
