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

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.transfer.TransferManager;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.Files;

public class S3DownloadCallable extends S3Callable<Void> {

    private static final long serialVersionUID = 1L;
    private final String bucketName;
    private final String key;

    public S3DownloadCallable(ClientHelper helper, String bucketName, String key) {
        super(helper);
        this.bucketName = bucketName;
        this.key = key;
    }

    @Override
    public Void invoke(TransferManager transferManager, File target, VirtualChannel channel) throws IOException {
        S3Object object = transferManager.getAmazonS3Client().getObject(bucketName, key);
        try (InputStream inputStream = new BufferedInputStream(object.getObjectContent());
             OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(target.toPath()))) {
            IOUtils.copy(inputStream, outputStream);
        }

        return null;
    }
}