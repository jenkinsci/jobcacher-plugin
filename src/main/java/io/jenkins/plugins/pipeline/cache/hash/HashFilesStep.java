package io.jenkins.plugins.pipeline.cache.hash;

import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.collect.ImmutableSet;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;

/**
 * Handles the 'hashFiles' step execution. When for example <b>hashFiles('**&#47;pom.xml')</b> is called from the pipeline then all
 * the poms of the project are hashed and this value is then returned.
 * @see HashFilesStepExecution
 */
public class HashFilesStep extends Step {

    private final String pattern;

    /**
     * @param pattern Glob pattern to filter workspace files (e.g. **&#47;pom.xml)
     */
    @DataBoundConstructor
    public HashFilesStep(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new HashFilesStepExecution(context, pattern);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, FilePath.class);
        }

        @Override
        public String getFunctionName() {
            return "hashFiles";
        }

        @Override
        public String getDisplayName() {
            return "creates a hash from workspace files";
        }
    }
}
