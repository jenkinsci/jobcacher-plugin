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

import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class S3UploadCallable extends S3BaseUploadCallable<Void> {

    private static final long serialVersionUID = 1L;

    private final String bucketName;
    private final String key;

    public S3UploadCallable(ClientHelper clientHelper, String bucketName, String key, Map<String, String> userMetadata, String storageClass, boolean useServerSideEncryption) {
        super(clientHelper, userMetadata, storageClass, useServerSideEncryption);
        this.bucketName = bucketName;
        this.key = key;
    }

    @Override
    public Void invoke(TransferManager transferManager, File source, VirtualChannel channel) throws IOException {
        if (!source.exists()) {
            return null;
        }

        PutObjectRequest request = new PutObjectRequest(bucketName, key, source);
        request.setMetadata(buildMetadata(source));
        transferManager.getAmazonS3Client().putObject(request);

        return null;
    }
}
