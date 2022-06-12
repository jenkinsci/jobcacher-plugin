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

package jenkins.plugins.jobcacher.pipeline;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.jobcacher.Cache;
import jenkins.plugins.jobcacher.CacheDescriptor;
import jenkins.plugins.jobcacher.CacheManager;
import jenkins.plugins.jobcacher.Messages;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Wrapping workflow step that automatically seeds the specified path with the previous run and on exit of the
 * block, saves that cache to the configured item storage.
 */
public class CacheStep extends Step {

    private final long maxCacheSize;
    private final List<Cache> caches;
    @DataBoundSetter
    public String defaultBranch = null;

    @DataBoundConstructor
    public CacheStep(long maxCacheSize, List<Cache> caches) {
        this.maxCacheSize = maxCacheSize;
        this.caches = caches;
    }

    @SuppressWarnings("unused")
    public long getMaxCacheSize() {
        return maxCacheSize;
    }

    public List<Cache> getCaches() {
        return caches;
    }

    @SuppressWarnings("unused")
    public String getDefaultBranch() {
        return defaultBranch;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new ExecutionImpl(context, maxCacheSize, caches, defaultBranch);
    }

    public static class ExecutionImpl extends GeneralNonBlockingStepExecution {

        private static final long serialVersionUID = 1L;

        private final long maxCacheSize;
        private final List<Cache> caches;
        private final String defaultBranch;

        protected ExecutionImpl(StepContext context, long maxCacheSize, List<Cache> caches, String defaultBranch) {
            super(context);

            this.maxCacheSize = maxCacheSize;
            this.caches = caches;
            this.defaultBranch = defaultBranch;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean start() throws Exception {
            StepContext context = getContext();

            Run run = context.get(Run.class);
            FilePath workspace = context.get(FilePath.class);
            Launcher launcher = context.get(Launcher.class);
            TaskListener listener = context.get(TaskListener.class);
            EnvVars initialEnvironment = context.get(EnvVars.class);

            List<Cache.Saver> cacheSavers = CacheManager.cache(GlobalItemStorage.get().getStorage(), run, workspace, launcher, listener, initialEnvironment, caches, defaultBranch);

            context.newBodyInvoker().
                    withContext(context).
                    withCallback(new ExecutionCallback(maxCacheSize, caches, cacheSavers)).
                    start();

            return false;
        }
    }

    public static class ExecutionCallback extends BodyExecutionCallback {

        private static final long serialVersionUID = 1L;

        private final long maxCacheSize;
        private final List<Cache> caches;
        private final List<Cache.Saver> cacheSavers;

        public ExecutionCallback(long maxCacheSize, List<Cache> caches, List<Cache.Saver> cacheSavers) {
            this.maxCacheSize = maxCacheSize;
            this.caches = caches;
            this.cacheSavers = cacheSavers;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            try {
                complete(context);

                context.onSuccess(result);
            } catch (Throwable t) {
                context.onFailure(t);
            }
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            try {
                // attempt to save the caches even though we failed
                complete(context);
            } catch (Throwable ignored) {
                // ignore error as we are failed anyway
            }

            context.onFailure(t);
        }

        public void complete(StepContext context) throws IOException, InterruptedException {
            Run<?, ?> run = context.get(Run.class);
            FilePath workspace = context.get(FilePath.class);
            Launcher launcher = context.get(Launcher.class);
            TaskListener listener = context.get(TaskListener.class);

            CacheManager.save(GlobalItemStorage.get().getStorage(), run, workspace, launcher, listener, maxCacheSize, caches, cacheSavers);
        }
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        /**
         * Constructor.
         */
        @SuppressWarnings("unused")
        public DescriptorImpl() {
            super(ExecutionImpl.class);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getFunctionName() {
            return "cache";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.CacheStep_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean takesImplicitBlockArgument() {
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
}
