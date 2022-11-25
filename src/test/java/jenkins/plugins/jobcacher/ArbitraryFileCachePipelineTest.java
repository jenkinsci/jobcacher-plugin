package jenkins.plugins.jobcacher;

import hudson.FilePath;
import hudson.model.Label;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class ArbitraryFileCachePipelineTest {

    private static final String DEFAULT_CACHE_VALIDITY_DECIDING_FILE_CONTENT = "abcdefghijklmnopqrstuvwxyz";

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    private static DumbSlave agent;

    @BeforeClass
    public static void startAgent() throws Exception {
        agent = jenkins.createSlave(Label.get("test-agent"));
    }

    @Test
    @WithTimeout(600)
    public void testMultipleCacheValidityDecidingFiles() throws Exception {
        String module1PackagesLock = "abcdefghijklmnopqrstuvwxyz";
        String module2PackagesLock = StringUtils.reverse(module1PackagesLock);

        WorkflowJob project = jenkins.createProject(WorkflowJob.class);
        String scriptedPipeline = ""
                + "node('test-agent') {\n"
                + "    writeFile text: '" + module1PackagesLock + "', file: 'module1/packages.lock.json'\n"
                + "    writeFile text: '" + module2PackagesLock + "', file: 'module2/packages.lock.json'\n"
                + "    cache(maxCacheSize: 100, caches: [arbitraryFileCache(path: 'test-path', cacheValidityDecidingFile: '**/packages.lock.json')]) {\n"
                + "        " + fileCreationCode("test-path", "test-file") + "\n"
                + "    }\n"
                + "}";
        project.setDefinition(new CpsFlowDefinition(scriptedPipeline, true));

        WorkflowRun run1 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run1.getLog())
                .contains("[Cache for test-path] Skip restoring cache as no up-to-date cache exists")
                .doesNotContain("expected output from test file")
                .contains("[Cache for test-path] Creating cache...");

        deleteCachedDirectoryInWorkspace(project);

        WorkflowRun run2 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run2.getLog())
                .contains("[Cache for test-path] Found cache in job specific caches")
                .contains("[Cache for test-path] Restoring cache...")
                .contains("expected output from test file")
                .contains("[Cache for test-path] Skip cache creation as the cache is up-to-date");
    }

    @Test
    @WithTimeout(600)
    public void testArbitraryFileCacheWithinPipelineWithCacheValidityDecidingFile() throws Exception {
        String cacheDefinition = "arbitraryFileCache(path: 'test-path', cacheValidityDecidingFile: 'cacheValidityDecidingFile.txt')";
        WorkflowJob project = createTestProject(cacheDefinition);

        WorkflowRun run1 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run1.getLog())
                .contains("[Cache for test-path] Skip restoring cache as no up-to-date cache exists")
                .doesNotContain("expected output from test file")
                .contains("[Cache for test-path] Creating cache...");

        deleteCachedDirectoryInWorkspace(project);

        WorkflowRun run2 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run2.getLog())
                .contains("[Cache for test-path] Found cache in job specific caches")
                .contains("[Cache for test-path] Restoring cache...")
                .contains("expected output from test file")
                .contains("[Cache for test-path] Skip cache creation as the cache is up-to-date");

        deleteCachedDirectoryInWorkspace(project);
        setProjectDefinition(project, cacheDefinition, StringUtils.reverse(DEFAULT_CACHE_VALIDITY_DECIDING_FILE_CONTENT));

        WorkflowRun run3 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run3.getLog())
                .contains("[Cache for test-path] Skip restoring cache as no up-to-date cache exists")
                .doesNotContain("expected output from test file")
                .contains("[Cache for test-path] Creating cache...");
    }

    @Test
    @WithTimeout(600)
    public void testChangeCompressionMethod() throws Exception {
        String tarGzCacheDefinition = "arbitraryFileCache(path: 'test-path', cacheValidityDecidingFile: 'cacheValidityDecidingFile.txt', compressionMethod: 'TARGZ')";
        WorkflowJob project = createTestProject(tarGzCacheDefinition);

        WorkflowRun run1 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run1.getLog())
                .contains("[Cache for test-path] Skip restoring cache as no up-to-date cache exists")
                .doesNotContain("expected output from test file")
                .contains("[Cache for test-path] Creating cache...");

        deleteCachedDirectoryInWorkspace(project);
        String zStandardCacheDefinition = "arbitraryFileCache(path: 'test-path', cacheValidityDecidingFile: 'cacheValidityDecidingFile.txt', compressionMethod: 'TAR_ZSTD')";
        setProjectDefinition(project, zStandardCacheDefinition);

        WorkflowRun run2 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run2.getLog())
                .contains("[Cache for test-path] Found cache in job specific caches")
                .contains("[Cache for test-path] Restoring cache...")
                .contains("expected output from test file")
                .contains("[Cache for test-path] Skip cache creation as the cache is up-to-date");

        deleteCachedDirectoryInWorkspace(project);
        setProjectDefinition(project, zStandardCacheDefinition, StringUtils.reverse(DEFAULT_CACHE_VALIDITY_DECIDING_FILE_CONTENT));

        WorkflowRun run3 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run3.getLog())
                .contains("[Cache for test-path] Skip restoring cache as no up-to-date cache exists")
                .doesNotContain("expected output from test file")
                .contains("[Cache for test-path] Delete existing cache as the compression method has been changed")
                .contains("[Cache for test-path] Creating cache...");
    }

    @Test
    @WithTimeout(600)
    public void testNonExistingCacheValidityDecidingFile() throws Exception {
        String cacheDefinition = "arbitraryFileCache(path: 'test-path', cacheValidityDecidingFile: 'cacheValidityDecidingFile-unknown.txt')";
        WorkflowJob project = createTestProject(cacheDefinition);

        WorkflowRun run1 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run1.getLog())
                .contains("[Cache for test-path] Skip restoring cache as no up-to-date cache exists")
                .doesNotContain("expected output from test file")
                .contains("[Cache for test-path] Creating cache...");

        deleteCachedDirectoryInWorkspace(project);

        WorkflowRun run2 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run2.getLog())
                .contains("[Cache for test-path] Skip restoring cache as no up-to-date cache exists")
                .doesNotContain("expected output from test file")
                .contains("[Cache for test-path] Creating cache...");
    }

    @Test
    @WithTimeout(600)
    public void testUncompressedArbitraryFileCacheWithinPipeline() throws Exception {
        testArbitraryFileCacheWithinPipeline("arbitraryFileCache(path: 'test-path')");
    }

    @Test
    @WithTimeout(600)
    public void testZipCompressedArbitraryFileCacheWithinPipeline() throws Exception {
        testArbitraryFileCacheWithinPipeline("arbitraryFileCache(path: 'test-path', compressionMethod: 'ZIP')");
    }

    @Test
    @WithTimeout(600)
    public void testTarGzCompressedArbitraryFileCacheWithinPipeline() throws Exception {
        testArbitraryFileCacheWithinPipeline("arbitraryFileCache(path: 'test-path', compressionMethod: 'TARGZ')");
    }

    @Test
    @WithTimeout(600)
    public void testZstandardCompressedArbitraryFileCacheWithinPipeline() throws Exception {
        testArbitraryFileCacheWithinPipeline("[$class: 'ArbitraryFileCache', path: 'test-path', compressionMethod: 'TAR_ZSTD']");
    }

    private void testArbitraryFileCacheWithinPipeline(String cacheDefinition) throws Exception {
        WorkflowJob project = createTestProject(cacheDefinition);

        WorkflowRun run1 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run1.getLog())
                .contains("[Cache for test-path] Skip restoring cache as no up-to-date cache exists")
                .doesNotContain("expected output from test file")
                .contains("[Cache for test-path] Creating cache...");

        deleteCachedDirectoryInWorkspace(project);

        WorkflowRun run2 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run2.getLog())
                .contains("[Cache for test-path] Found cache in job specific caches")
                .contains("[Cache for test-path] Restoring cache...")
                .contains("expected output from test file")
                .contains("[Cache for test-path] Creating cache...");
    }

    private WorkflowJob createTestProject(String cacheDefinition) throws IOException {
        WorkflowJob project = jenkins.createProject(WorkflowJob.class);
        setProjectDefinition(project, cacheDefinition, DEFAULT_CACHE_VALIDITY_DECIDING_FILE_CONTENT);

        return project;
    }

    private void setProjectDefinition(WorkflowJob project, String cacheDefinition) {
        setProjectDefinition(project, cacheDefinition, DEFAULT_CACHE_VALIDITY_DECIDING_FILE_CONTENT);
    }

    private void setProjectDefinition(WorkflowJob project, String cacheDefinition, String cacheValidityDecidingFileContent) {
        String scriptedPipeline = ""
                + "node('test-agent') {\n"
                + "    writeFile text: '" + cacheValidityDecidingFileContent + "', file: 'cacheValidityDecidingFile.txt'\n"
                + "    cache(maxCacheSize: 100, caches: [" + cacheDefinition + "]) {\n"
                + "        " + fileCreationCode("test-path", "test-file") + "\n"
                + "    }\n"
                + "}";
        project.setDefinition(new CpsFlowDefinition(scriptedPipeline, true));
    }

    private String fileCreationCode(String folder, String file) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return "bat '''" + fileCreationCodeForWindows(folder, file + ".bat") + "'''";
        } else {
            return "sh '''" + fileCreationCodeForLinux(folder, file + ".sh") + "'''";
        }
    }

    private String fileCreationCodeForWindows(String folder, String file) {
        String filePath = folder + "/" + file;
        return ""
                + "echo off\n"
                + "if exist \"" + filePath + "\" \"" + filePath + "\"\n"
                + "if not exist \"" + folder + "\" mkdir \"" + folder + "\"\n"
                + "echo echo expected output from test file > \"" + filePath + "\"\n";
    }

    private String fileCreationCodeForLinux(String folder, String file) {
        String filePath = folder + "/" + file;
        return ""
                + "set +x\n"
                + "[ -f '" + filePath + "' ] && './" + filePath + "'\n"
                + "mkdir -p '" + folder + "'\n"
                + "echo echo expected output from test file > '" + filePath + "'\n"
                + "chmod a+x '" + filePath + "'\n";
    }

    private void deleteCachedDirectoryInWorkspace(WorkflowJob project) throws IOException, InterruptedException {
        FilePath workspace = agent.getWorkspaceFor(project);
        if (workspace != null) {
            workspace.child("test-path").deleteRecursive();
        }
    }
}
