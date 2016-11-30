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

package jenkins.plugins.jobcacher;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * Created by hayep on 11/28/2016.
 */
public class ArbitraryFileCache extends Cache {
    private String path;
    private String includes = "**/*";
    private String excludes;

    @DataBoundConstructor
    public ArbitraryFileCache(String path, String includes, String excludes) {
        this.path = path;
        this.includes = StringUtils.isNotBlank(includes) ? includes : "**/*";
        this.excludes = excludes;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getIncludes() {
        return includes;
    }

    public void setIncludes(String includes) {
        this.includes = includes;
    }

    public String getExcludes() {
        return excludes;
    }

    public void setExcludes(String excludes) {
        this.excludes = excludes;
    }

    @Override
    public String getTitle() {
        return Messages.ArbitraryFileCache_displayName();
    }

    @Override
    public long calculateSize(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        // Locate the cache on the master node
        FilePath targetDirectory = workspace.child(path);

        return targetDirectory.act(new DirectorySize(includes, excludes));
    }

    @Override
    public void cache(File cacheDir, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        // Get a source dir for cached files for this path
        FilePath sourceDir = deriveCachePath(cacheDir, path);

        // Resolve path variables if any
        String expandedPath = initialEnvironment.expand(path);

        cachePath(sourceDir, workspace, listener, expandedPath, includes, excludes);
    }

    @Override
    public void save(File cacheDir, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        // Get a target dir for cached files for this path
        FilePath targetDir = deriveCachePath(cacheDir, path);

        // Resolve path variables if any
        String expandedPath = build.getEnvironment(listener).expand(path);

        savePath(targetDir, workspace, listener, expandedPath, includes, excludes);
    }

    public DirectoryBrowserSupport doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {

        Job job = req.findAncestorObject(Job.class);

        FilePath cachePath = deriveCachePath(CacheWrapper.findCacheDir(job), path);
        if (!cachePath.exists()) {
            req.getView(this,"noCache.jelly").forward(req,rsp);
            return null;
        } else {
            return new DirectoryBrowserSupport(job, cachePath, "Cache of " + path, "folder.png", true);
        }
    }

    @Extension
    public static final class DescriptorImpl extends CacheDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.ArbitraryFileCache_displayName();
        }
    }
}
