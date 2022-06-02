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

package jenkins.plugins.itemstorage;

import hudson.FilePath;
import hudson.model.Job;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;

/**
 * The abstraction to interact with a particular object path
 *
 * @author Peter Hayes
 */
public abstract class ObjectPath {

    /**
     * Get a relative child path of this ObjectPath
     *
     * @param path The relative path
     * @return The child instance
     * @throws IOException
     * @throws InterruptedException
     */
    public abstract ObjectPath child(String path) throws IOException, InterruptedException;

    /**
     * Copy this object path to the file path.  Implementations may utilize last modified time to only copy what
     * has changed.
     *
     * @param target Where to recursively copy the target to
     * @return The number of files actually copied
     * @throws IOException
     * @throws InterruptedException
     */
    public int copyRecursiveTo(FilePath target) throws IOException, InterruptedException {
        return copyRecursiveTo("**/*", target);
    }

    /**
     * Copy with a mask
     *
     * @param fileMask
     * @param target
     * @return The number of files actually copied
     * @throws IOException
     * @throws InterruptedException
     */
    public int copyRecursiveTo(String fileMask, FilePath target) throws IOException, InterruptedException {
        return copyRecursiveTo(fileMask, null, true, target);
    }

    /**
     * Copy with mask and exclusions
     *
     * @param fileMask
     * @param excludes
     * @param target
     * @return The number of files actually copied
     * @throws IOException
     * @throws InterruptedException
     */
    public abstract int copyRecursiveTo(String fileMask, String excludes, boolean useDefaultExcludes, FilePath target) throws IOException, InterruptedException;

    /**
     * Copy to this object path from the pass file source
     *
     * @param source
     * @return The number of file copied
     * @throws IOException
     * @throws InterruptedException
     */
    public int copyRecursiveFrom(FilePath source) throws IOException, InterruptedException {
        return copyRecursiveFrom("**/*", source);
    }

    /**
     * Copy to this object path from the pass file source
     *
     * @param source
     * @param fileMask
     * @return The number of file copied
     * @throws IOException
     * @throws InterruptedException
     */
    public int copyRecursiveFrom(String fileMask, FilePath source) throws IOException, InterruptedException {
        return copyRecursiveFrom(fileMask, null, true, source);
    }

    /**
     * Copy to this object path from the pass file source
     *
     * @param fileMask
     * @param excludes
     * @param source
     * @return The number of file copied
     * @throws IOException
     * @throws InterruptedException
     */
    public abstract int copyRecursiveFrom(String fileMask, String excludes, boolean useDefaultExcludes, FilePath source) throws IOException, InterruptedException;

    /**
     * Check if this path actually exists
     *
     * @return true if so, false otherwise
     * @throws IOException
     * @throws InterruptedException
     */
    public abstract boolean exists() throws IOException, InterruptedException;

    /**
     * Recursively delete all contents within the path
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public abstract void deleteRecursive() throws IOException, InterruptedException;

    /**
     * Support browsing the cache via UI
     *
     * @param job
     * @return
     */
    public abstract HttpResponse browse(StaplerRequest request, StaplerResponse response, Job<?, ?> job, String name) throws IOException;
}
