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

import hudson.FilePath;
import hudson.model.Job;
import jenkins.plugins.itemstorage.ObjectPath;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.Collections;

/**
 * Implements ObjectPath API
 *
 * @author Peter Hayes
 */
public class S3ObjectPath extends ObjectPath {
    private final S3Profile profile;
    private final String bucketName;
    private final String region;
    private final String fullName;
    private final String path;

    public S3ObjectPath(S3Profile profile, String bucketName, String region, String fullName, String path) {
        this.profile = profile;
        this.bucketName = bucketName;
        this.region = region;
        this.fullName = fullName;
        this.path = path;
    }

    @Override
    public S3ObjectPath child(String childPath) throws IOException, InterruptedException {
        return new S3ObjectPath(profile, bucketName, region, fullName, path + "/" + childPath);
    }

    @Override
    public int copyRecursiveTo(String fileMask, String excludes, boolean useDefaultExcludes, FilePath target) throws IOException, InterruptedException {
        return profile.download(bucketName, fullName + "/" + path, fileMask, excludes, useDefaultExcludes, target);
    }

    @Override
    public int copyRecursiveFrom(String fileMask, String excludes, boolean useDefaultExcludes, FilePath source) throws IOException, InterruptedException {
        return profile.upload(bucketName, fullName + "/" + path, fileMask, excludes, useDefaultExcludes, source, Collections.emptyMap(), null, false);
    }

    @Override
    public boolean exists() throws IOException, InterruptedException {
        return profile.exists(bucketName, fullName + "/" + path);
    }

    @Override
    public void deleteRecursive() throws IOException, InterruptedException {
        profile.delete(bucketName, fullName + "/" + path);
    }

    @Override
    public HttpResponse browse(StaplerRequest request, StaplerResponse response, Job<?, ?> job, String name) throws IOException {
        // For now attempt to forward to s3 for browsing
        response.sendRedirect2("https://console.aws.amazon.com/s3/home?region=" + region + "#&bucket=" + bucketName + "&prefix=" + fullName + "/" + path + "/");
        return null;
    }
}
