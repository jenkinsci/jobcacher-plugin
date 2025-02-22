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

package jenkins.plugins.itemstorage.local;

import hudson.FilePath;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Job;
import jenkins.plugins.itemstorage.ObjectPath;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

import java.io.IOException;

/**
 * This implements the on-controller storage for object paths.
 *
 * @author Peter Hayes
 */
public class LocalObjectPath extends ObjectPath {

    private final FilePath file;

    public LocalObjectPath(FilePath file) {
        this.file = file;
    }

    @Override
    public ObjectPath child(String path) throws IOException, InterruptedException {
        return new LocalObjectPath(file.child(path));
    }

    @Override
    public void copyTo(FilePath target) throws IOException, InterruptedException {
        file.copyTo(target);
    }

    @Override
    public void copyFrom(FilePath source) throws IOException, InterruptedException {
        file.copyFrom(source);
    }

    @Override
    public boolean exists() throws IOException, InterruptedException {
        return file.exists();
    }

    @Override
    public void deleteRecursive() throws IOException, InterruptedException {
        file.deleteRecursive();
    }

    @Override
    public HttpResponse browse(StaplerRequest2 request, StaplerResponse2 response, Job<?, ?> job, String name) {
        return new DirectoryBrowserSupport(job, file, "Cache of " + name, "folder.png", true);
    }

    protected String getPath() {
        return file.getRemote();
    }

}
