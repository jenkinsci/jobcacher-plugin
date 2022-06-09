package jenkins.plugins.jobcacher;

import hudson.model.Label;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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

    private static final String DEFAULT_CACHE_VALIDITY_DECIDING_FILE_CONTENT = "abcdefghijklmnopqrstuvwxyz";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private DumbSlave slave;

    @Before
    public void startSlave() throws Exception {
        slave = jenkins.createSlave(Label.get("slave"));
    }

    @Test
    @WithTimeout(600)
    public void testArbitraryFileCacheWithinPipelineWithCacheValidityDecidingFile() throws Exception {
        String cacheDefinition = "[$class: 'ArbitraryFileCache', path: 'node_modules', cacheValidityDecidingFile: 'cacheValidityDecidingFile.txt']";
        WorkflowJob project = createTestProject(cacheDefinition);

        WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run.getLog())
                .contains("[Cache for node_modules] Skip restoring cache as no up-to-date cache exists")
                .contains("added 1 package, and audited 2 packages in")
                .contains("[Cache for node_modules] Creating cache...");

        deleteNodeModulesInWorkspace(project);
        run = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run.getLog())
                .contains("[Cache for node_modules] Found cache in job specific caches")
                .contains("[Cache for node_modules] Restoring cache...")
                .contains("up to date, audited 2 packages in")
                .contains("[Cache for node_modules] Skip cache creation as the cache is up-to-date");

        deleteNodeModulesInWorkspace(project);
        setProjectDefinition(project, cacheDefinition, StringUtils.reverse(DEFAULT_CACHE_VALIDITY_DECIDING_FILE_CONTENT));
        run = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run.getLog())
                .contains("[Cache for node_modules] Skip restoring cache as no up-to-date cache exists")
                .contains("added 1 package, and audited 2 packages in")
                .contains("[Cache for node_modules] Creating cache...");
    }

    @Test
    @WithTimeout(600)
    public void testUncompressedArbitraryFileCacheWithinPipeline() throws Exception {
        testArbitraryFileCacheWithinPipeline("[$class: 'ArbitraryFileCache', path: 'node_modules']");
    }

    @Test
    @WithTimeout(600)
    public void testZipCompressedArbitraryFileCacheWithinPipeline() throws Exception {
        testArbitraryFileCacheWithinPipeline("[$class: 'ArbitraryFileCache', path: 'node_modules', compressionMethod: 'ZIP']");
    }

    @Test
    @WithTimeout(600)
    public void testTarGzCompressedArbitraryFileCacheWithinPipeline() throws Exception {
        testArbitraryFileCacheWithinPipeline("[$class: 'ArbitraryFileCache', path: 'node_modules', compressionMethod: 'TARGZ']");
    }

    private void testArbitraryFileCacheWithinPipeline(String cacheDefinition) throws Exception {
        WorkflowJob project = createTestProject(cacheDefinition);

        WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run.getLog())
                .contains("[Cache for node_modules] Skip restoring cache as no up-to-date cache exists")
                .contains("added 1 package, and audited 2 packages in")
                .contains("[Cache for node_modules] Creating cache...");

        deleteNodeModulesInWorkspace(project);
        run = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run.getLog())
                .contains("[Cache for node_modules] Found cache in job specific caches")
                .contains("[Cache for node_modules] Restoring cache...")
                .contains("up to date, audited 2 packages in")
                .contains("[Cache for node_modules] Creating cache...");
    }

    private WorkflowJob createTestProject(String cacheDefinition) throws IOException {
        WorkflowJob project = jenkins.createProject(WorkflowJob.class);
        setProjectDefinition(project, cacheDefinition, DEFAULT_CACHE_VALIDITY_DECIDING_FILE_CONTENT);

        return project;
    }

    private void setProjectDefinition(WorkflowJob project, String cacheDefinition, String cacheValidityDecidingFileContent) throws IOException {
        String projectEnvConfigFileContent = readTestResource("project-env.toml");
        String packageJsonFileContent = readTestResource("package.json");

        project.setDefinition(createOsSpecificPipelineDefinition("" +
                "node('slave') {\n" +
                "  writeFile text: '''" + projectEnvConfigFileContent + "''', file: 'project-env.toml'\n" +
                "  withProjectEnv {\n" +
                "    writeFile text: '''" + packageJsonFileContent + "''', file: 'package.json'\n" +
                "    writeFile text: '" + cacheValidityDecidingFileContent + "', file: 'cacheValidityDecidingFile.txt'\n" +
                "    cache(maxCacheSize: 100, caches: [" + cacheDefinition + "]) {\n" +
                "      println \"PATH: ${env.PATH}\"\n" +
                "      sh 'npm install'\n" +
                "    }\n" +
                "  }\n" +
                "}"));
    }

    private void deleteNodeModulesInWorkspace(WorkflowJob project) throws IOException, InterruptedException {
        slave.getWorkspaceFor(project).child("node_modules").deleteRecursive();
    }

    private String readTestResource(String resource) throws IOException {
        return IOUtils.toString(getClass().getResource(resource), StandardCharsets.UTF_8);
    }

    private CpsFlowDefinition createOsSpecificPipelineDefinition(String pipelineDefinition) {
        return new CpsFlowDefinition(SystemUtils.IS_OS_WINDOWS ?
                pipelineDefinition.replaceAll("sh '", "bat '") :
                pipelineDefinition, true);
    }

}
