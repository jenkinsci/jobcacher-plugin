package jenkins.plugins.jobdsl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.FreeStyleProject;
import javaposse.jobdsl.plugin.ExecuteDslScripts;
import javaposse.jobdsl.plugin.LookupStrategy;
import javaposse.jobdsl.plugin.RemovedJobAction;
import javaposse.jobdsl.plugin.RemovedViewAction;
import jenkins.plugins.jobcacher.CacheWrapper;

public class JobDslTest {

    public static final String ANSIBLE_DSL_GROOVY_PLAYBOOK = "jobdsl/playbook.groovy";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    @Issue("https://github.com/jenkinsci/jobcacher-plugin/issues/271")
    public void shouldCreateFreestyleJob() throws Exception {
        runJobDsl("/jobdsl/freestyle.groovy", jenkins);
        CacheWrapper step = jenkins.jenkins.getItemByFullName("freestyle", FreeStyleProject.class).getBuildWrappersList().get(CacheWrapper.class);
        assertNotNull(step);
        assertThat(step.getCaches(), hasSize(2));
        assertThat(step.getSkipSave(), is(true));
        assertThat(step.getSkipRestore(), is(false));
        assertThat(step.getMaxCacheSize(), is(1024L));
        assertThat(step.getDefaultBranch(), is("main"));
    }

    private void runJobDsl(String script, JenkinsRule rule) throws Exception {
        FreeStyleProject job = rule.createFreeStyleProject();
        String scriptText = IOUtils.toString(JobDslTest.class.getResourceAsStream(script), StandardCharsets.UTF_8);
        ExecuteDslScripts builder = new ExecuteDslScripts();
        builder.setScriptText(scriptText);
        builder.setRemovedJobAction(RemovedJobAction.DELETE);
        builder.setRemovedViewAction(RemovedViewAction.DELETE);
        builder.setLookupStrategy(LookupStrategy.JENKINS_ROOT);
        job.getBuildersList().add(builder);
        rule.buildAndAssertSuccess(job);
    }

}
