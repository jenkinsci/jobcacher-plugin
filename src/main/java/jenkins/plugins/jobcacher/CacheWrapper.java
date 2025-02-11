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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.model.Jenkins;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.ItemStorage;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * This is the entry point for the caching capability when used as a build wrapper.
 *
 * @author Peter Hayes
 */
public class CacheWrapper extends SimpleBuildWrapper {

    private Long maxCacheSize;
    private boolean skipSave;
    private boolean skipRestore;
    private List<Cache> caches;
    private String defaultBranch;

    @DataBoundConstructor
    public CacheWrapper(List<Cache> caches) {
        setCaches(caches);
    }

    @SuppressWarnings("unused")
    public ItemStorage<?> getStorage() {
        return GlobalItemStorage.get().getStorage();
    }

    @SuppressWarnings("unused")
    public Long getMaxCacheSize() {
        return maxCacheSize;
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setMaxCacheSize(Long maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    @SuppressWarnings("unused")
    public boolean getSkipSave() {
        return skipSave;
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setSkipSave(boolean skipSave) {
        this.skipSave = skipSave;
    }

    @SuppressWarnings("unused")
    public boolean getSkipRestore() {
        return skipRestore;
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setSkipRestore(boolean skipRestore) {
        this.skipRestore = skipRestore;
    }

    @SuppressWarnings("unused")
    public String getDefaultBranch() {
        return defaultBranch;
    }

    @DataBoundSetter
    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public List<Cache> getCaches() {
        return wrapList(caches, Collections::unmodifiableList);
    }

    public void setCaches(List<Cache> caches) {
        this.caches = wrapList(caches, ArrayList::new);
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        List<Cache.Saver> cacheSavers = CacheManager.cache(getStorage(), build, workspace, launcher, listener, initialEnvironment, getCaches(), getDefaultBranch(), skipRestore);

        context.setDisposer(new CacheDisposer(getStorage(), getMaxCacheSize(), getSkipSave(), getCaches(), cacheSavers, defaultBranch));
    }

    private static <T> List<T> wrapList(List<T> list, Function<List<T>, List<T>> listFactory) {
        return list == null ? Collections.emptyList() : listFactory.apply(list);
    }

    @Extension
    @Symbol("jobcacher")
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.CacheWrapper_DisplayName();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @SuppressWarnings("unused")
        public List<CacheDescriptor> getCacheDescriptors() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins != null) {
                return jenkins.getDescriptorList(Cache.class);
            } else {
                return Collections.emptyList();
            }
        }
    }

    private static class CacheDisposer extends Disposer {

        private static final long serialVersionUID = 1L;

        private final ItemStorage<?> storage;
        private final Long maxCacheSize;
        private final boolean skipSave;
        private final List<Cache> caches;
        private final List<Cache.Saver> cacheSavers;
        private final String defaultBranch;

        @DataBoundConstructor
        public CacheDisposer(ItemStorage<?> storage, Long maxCacheSize, boolean skipSave, List<Cache> caches, List<Cache.Saver> cacheSavers, String defaultBranch) {
            this.storage = storage;
            this.maxCacheSize = maxCacheSize;
            this.skipSave = skipSave;
            this.caches = caches;
            this.cacheSavers = cacheSavers;
            this.defaultBranch = defaultBranch;
        }

        @Override
        public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            if (build.getResult() != Result.FAILURE && build.getResult() != Result.ABORTED && !skipSave) {
                CacheManager.save(storage, build, workspace, launcher, listener, maxCacheSize, caches, cacheSavers, defaultBranch);
            }
        }
    }
}
