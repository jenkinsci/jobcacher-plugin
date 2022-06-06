package jenkins.plugins.jobcacher;

import hudson.model.Label;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ArbitraryFileCachePipelineTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private DumbSlave slave;

    @Before
    public void startSlave() throws Exception {
        slave = jenkins.createSlave(Label.get("slave"));
    }

    @Test
    @WithTimeout(600)
    public void testArbitraryFileCacheWithinPipeline() throws Exception {
        String projectEnvConfigFileContent = readTestResource("project-env.toml");
        String packageJsonFileContent = readTestResource("package.json");
        String packageLockJsonFileContent = readTestResource("package-lock.json");

        WorkflowJob project = jenkins.createProject(WorkflowJob.class);
        project.setDefinition(createOsSpecificPipelineDefinition("" +
                "node('slave') {\n" +
                "  writeFile text: '''" + projectEnvConfigFileContent + "''', file: 'project-env.toml'\n" +
                "  withProjectEnv {\n" +
                "    writeFile text: '''" + packageJsonFileContent + "''', file: 'package.json'\n" +
                "    writeFile text: '''" + packageLockJsonFileContent + "''', file: 'package-lock.json'\n" +
                "    cache(maxCacheSize: 100, caches: [[$class: 'ArbitraryFileCache', path: 'node_modules']]) {\n" +
                "      println \"PATH: ${env.PATH}\"\n" +
                "      sh 'npm install'\n" +
                "    }\n" +
                "  }\n" +
                "}"));

        WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run.getLog())
                .contains("Skip caching as no cache exists for node_modules")
                .contains("added 1 package, and audited 2 packages in")
                .contains("Storing node_modules in cache");

        slave.getWorkspaceFor(project).deleteContents();

        run = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run.getLog())
                .contains("Caching node_modules to executor")
                .contains("up to date, audited 2 packages in")
                .contains("Storing node_modules in cache");
    }

    private String readTestResource(String resource) throws IOException {
        return IOUtils.toString(getClass().getResource(resource), StandardCharsets.UTF_8);
    }

    private CpsFlowDefinition createOsSpecificPipelineDefinition(String pipelineDefinition) {
        return new CpsFlowDefinition(SystemUtils.IS_OS_WINDOWS ?
                pipelineDefinition.replace("sh", "bat") :
                pipelineDefinition, true);
    }

}
