package jenkins.plugins.jobcacher;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.Result;

public class MandatoryPropertiesTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void testLegacyCachesParameterIsMissing() throws Exception {
        // GIVEN
        WorkflowJob p = j.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("node {\n"
                + "    cache(maxCacheSize: 100) {}\n"
                + "}", true));

        // WHEN
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatus(Result.FAILURE, b);
    }

    @Test
    public void testLegacyMaxCacheSizeParameterIsMissing() throws Exception {
        // GIVEN
        WorkflowJob p = j.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("node {\n"
                + "    dir ('sub') {}\n"
                + "    cache(caches: [[$class: 'ArbitraryFileCache', path: 'sub', compressionMethod: 'NONE']]) {}"
                + "}", true));

        // WHEN
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatus(Result.FAILURE, b);
    }

    @Test
    public void testPathParameterIsMissing() throws Exception {
        // GIVEN
        WorkflowJob p = j.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("node {\n"
                + "    cache(key: 'a') {}\n"
                + "}", true));

        // WHEN
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatus(Result.FAILURE, b);
    }

    @Test
    public void testKeyParameterIsMissing() throws Exception {
        // GIVEN
        WorkflowJob p = j.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("node {\n"
                + "    dir ('sub') {}\n"
                + "    cache(path: 'sub') {}\n"
                + "}", true));

        // WHEN
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(b);

        // THEN
        j.assertBuildStatus(Result.FAILURE, b);
    }

}
