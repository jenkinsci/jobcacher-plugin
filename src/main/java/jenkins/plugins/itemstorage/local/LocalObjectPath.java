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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.FilePath;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Job;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import jenkins.plugins.itemstorage.ObjectPath;

/**
 * This implements the on master storage for object paths
 *
 * @author Peter Hayes
 */
public class LocalObjectPath extends ObjectPath {
    private static final Logger LOGGER = Logger.getLogger(LocalObjectPath.class.getName());

    private FilePath file;

    public LocalObjectPath(FilePath file) {
        this.file = file;
    }

    @Override
    public ObjectPath child(String path) throws IOException, InterruptedException {
        return new LocalObjectPath(file.child(path));
    }

    @Override
    public int copyRecursiveTo(String fileMask, String excludes, boolean useDefaultExcludes, FilePath target) throws IOException, InterruptedException {
        LOGGER.info("Copying from " + file + " to " + target);
        return file.copyRecursiveTo(new IsModifiedGlob(fileMask, excludes, useDefaultExcludes, target), target, fileMask);
    }

    @Override
    public int copyRecursiveFrom(String fileMask, String excludes, boolean useDefaultExcludes, FilePath source) throws IOException, InterruptedException {
        LOGGER.info("Copying from " + source + " to " + file);
        return source.copyRecursiveTo(new IsModifiedGlob(fileMask, excludes, useDefaultExcludes, file), file, fileMask);
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
    public HttpResponse browse(StaplerRequest request, StaplerResponse response, Job<?, ?> job, String name) {
        return new DirectoryBrowserSupport(job, file, "Cache of " + name, "folder.png", true);
    }

    /**
     * Scanner that filters out files that are up to date
     */
    private static class IsModifiedGlob extends DirScanner.Glob {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        private final FilePath toCompare;

        public IsModifiedGlob(String includes, String excludes, boolean useDefaultExcludes, FilePath toCompare) {
            super(includes, excludes, useDefaultExcludes);
            this.toCompare = toCompare;
        }

        @Override
        public void scan(File dir, final FileVisitor visitor) throws IOException {
            super.scan(dir, new IsNotThereOrOlderVisitor(toCompare, visitor));
        }
    }

    /**
     * Visitor that checks modification time if source is newer than target.  If so, it calls delegate and updates
     * target file modification time to be same as source.
     */
    public static class IsNotThereOrOlderVisitor extends FileVisitor implements Serializable {
        
        /**
         *
         */
        private static final long serialVersionUID = 1L;
        
        private FilePath toCompare;
        private FileVisitor delegate;

        public IsNotThereOrOlderVisitor(FilePath toCompare, FileVisitor delegate) {
            this.toCompare = toCompare;
            this.delegate = delegate;
        }

        @Override
        public void visit(File f, final String relativePath) throws IOException {
            // check if file is more recent than base one
            try {
                FilePath targetFile = toCompare.child(relativePath);
                if (!targetFile.exists() || f.lastModified() > targetFile.lastModified()) {
                    delegate.visit(f, relativePath);

                    // Only set modification date if the file ended up being copied - pretty chatty!
                    if (targetFile.exists()) {
                        targetFile.touch(f.lastModified());
                    }
                }
            } catch (InterruptedException ie) {
                LOGGER.info("Interrupted while checking file [" + f + "] skipping and reinterrupting");

                Thread.currentThread().interrupt();
            }
        }
    }
}
