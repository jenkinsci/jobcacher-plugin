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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.plugins.jobcacher.Cache;
import jenkins.plugins.jobcacher.CacheDescriptor;
import jenkins.plugins.jobcacher.Messages;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Wrapping workflow step that automatically seeds the specified path with the previous run and on exit of the
 * block, saves that cache to the configured item storage.
 */
public class CacheStep extends Step {

    private final List<Cache> caches;
    private Long maxCacheSize;
    private boolean skipSave;
    private boolean skipRestore;

    private String defaultBranch;

    @DataBoundConstructor
    public CacheStep(Long maxCacheSize, List<Cache> caches) {
        this.maxCacheSize = maxCacheSize;
        this.caches = caches;
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    @SuppressWarnings("unused")
    public String getDefaultBranch() {
        return defaultBranch;
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setMaxCacheSize(Long maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    @SuppressWarnings("unused")
    public Long getMaxCacheSize() {
        return maxCacheSize;
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setSkipSave(boolean skipSave) {
        this.skipSave = skipSave;
    }

    @SuppressWarnings("unused")
    public boolean getSkipSave() {
        return this.skipSave;
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setSkipRestore(boolean skipRestore) {
        this.skipRestore = skipRestore;
    }

    @SuppressWarnings("unused")
    public boolean getSkipRestore() {
        return skipRestore;
    }

    public List<Cache> getCaches() {
        return caches;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new CacheStepExecution(context, maxCacheSize, skipSave, skipRestore, caches, defaultBranch);
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.emptySet();
        }

        @Override
        public String getFunctionName() {
            return "cache";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.CacheStep_DisplayName();
        }

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
