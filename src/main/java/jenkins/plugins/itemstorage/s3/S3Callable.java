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

import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;

/**
 * Executes a possibly remote S3 operation setting up / tearing down a transfer manager.
 * @param <T>
 */
abstract class S3Callable<T> extends MasterToSlaveFileCallable<T> {
    private static final long serialVersionUID = 1L;

    private ClientHelper helper;

    S3Callable(ClientHelper helper) {
        this.helper = helper;
    }

    /**
     * Override this if you don't want a transfer manager
     */
    @Override
    public T invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        TransferManager transferManager = TransferManagerBuilder.standard()
            .withS3Client(helper.client())
            .withDisableParallelDownloads(!helper.supportsParallelDownloads())
            .build();

        try {
            return invoke(transferManager, f, channel);
        } finally {
            transferManager.shutdownNow();
        }
    }

    /**
     * Override this if you do want a transfer manager
     */
    public T invoke(TransferManager transferManager, File f, VirtualChannel channel) throws IOException, InterruptedException {
        return null;
    }
}
