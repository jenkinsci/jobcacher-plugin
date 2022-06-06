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
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.ObjectPath;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.*;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * This class implements a Cache where the user can configure a path on the executor that will be cached.  Users can
 * reference environment variables on the executor in the path and supply an includes and excludes pattern to limit the
 * files that are cached.
 *
 * @author Peter Hayes
 */
public class ArbitraryFileCache extends Cache {
    private static Logger LOGGER = Logger.getLogger(ArbitraryFileCache.class.getName());

    private static final long serialVersionUID = 1L;

    private String path;
    private String includes = "**/*";
    private String excludes;
    private boolean useDefaultExcludes = true;

    @DataBoundConstructor
    public ArbitraryFileCache(String path, String includes, String excludes) {
        this.path = path;
        this.includes = StringUtils.isNotBlank(includes) ? includes : "**/*";
        this.excludes = excludes;
    }

    @DataBoundSetter
    public void setUseDefaultExcludes(boolean useDefaultExcludes) {
        this.useDefaultExcludes = useDefaultExcludes;
    }

    public boolean getUseDefaultExcludes() {
        return useDefaultExcludes;
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
    public Saver cache(ObjectPath cache, ObjectPath defaultCache, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        // Get a source dir for cached files for this path
        ObjectPath source = cache.child(deriveCachePath(path));
        ObjectPath defaultSource = null;

        if (defaultCache != null) {
            defaultSource = defaultCache.child(deriveCachePath(path));
        }

        // Resolve path variables if any
        String expandedPath = initialEnvironment.expand(path);

        cachePath(source, defaultSource, workspace, listener, expandedPath, includes, excludes, useDefaultExcludes);

        return new SaverImpl(expandedPath);
    }

    private class SaverImpl extends Saver {

        private static final long serialVersionUID = 1L;

        private String expandedPath;

        public SaverImpl(String expandedPath) {
            this.expandedPath = expandedPath;
        }

        @Override
        public long calculateSize(ObjectPath objectPath, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            // Locate the cache on the master node
            FilePath targetDirectory = workspace.child(path);

            return targetDirectory.act(new DirectorySize(includes, excludes));
        }

        @Override
        public void save(ObjectPath cache, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            // Get a target dir for cached files for this path
            ObjectPath target = cache.child(deriveCachePath(path));

            savePath(target, workspace, listener, expandedPath, includes, excludes, useDefaultExcludes);
        }
    }

    public HttpResponse doDynamic(StaplerRequest req, StaplerResponse rsp, @AncestorInPath Job job) throws IOException, ServletException, InterruptedException {

        ObjectPath cache = CacheManager.getCachePath(GlobalItemStorage.get().getStorage(), job).child(deriveCachePath(path));

        if (!cache.exists()) {
            req.getView(this,"noCache.jelly").forward(req,rsp);
            return null;
        } else {
            return cache.browse(req, rsp, job, path);
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
