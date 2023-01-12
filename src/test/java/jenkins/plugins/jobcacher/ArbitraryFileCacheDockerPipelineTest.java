package jenkins.plugins.jobcacher;

import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import jenkins.plugins.git.GitSampleRepoRule;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ArbitraryFileCacheDockerPipelineTest {

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public GitSampleRepoRule gitRepoRule = new GitSampleRepoRule();

    @Test
    @WithTimeout(600)
    public void testArbitraryFileCacheWithinDockerContainer() throws Exception {
        String cacheDefinition = "arbitraryFileCache(path: '/tmp/test-path')";
        WorkflowJob project = createTestProject(cacheDefinition);

        WorkflowRun run1 = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));
        assertThat(run1.getLog())
                .contains("[Cache for /tmp/test-path with id 72b62be1919b26a414e7b7bd4265f684] Searching cache in job specific caches...")
                .contains("[Cache for /tmp/test-path with id 72b62be1919b26a414e7b7bd4265f684] Searching cache in default caches...")
                .contains("[Cache for /tmp/test-path with id 72b62be1919b26a414e7b7bd4265f684] Skip restoring cache as no up-to-date cache exists")
                .doesNotContain("expected output from test file")
                .contains("[Cache for /tmp/test-path with id 72b62be1919b26a414e7b7bd4265f684] Cannot create cache as the path does not exist")
                .contains("[Cache for /tmp/test-path with id 72b62be1919b26a414e7b7bd4265f684] Note that paths outside the workspace while using the Docker Pipeline plugin are not supported");
    }

    private WorkflowJob createTestProject(String cacheDefinition) throws Exception {
        WorkflowJob project = jenkins.createProject(WorkflowJob.class);

        String pipelineScript = ""
                + "pipeline {\n"
                + "   agent {\n"
                + "      docker { image 'alpine' }\n"
                + "   }\n"
                + "   stages {\n"
                + "      stage('Test') {\n"
                + "         steps {\n"
                + "      cache(maxCacheSize: 100, caches: [" + cacheDefinition + "]) {\n"
                + "            " + fileCreationCode("/tmp/test-path", "test-file") + "\n"
                + "         }\n"
                + "         }\n"
                + "      }\n"
                + "   }\n"
                + "}";

        gitRepoRule.init();
        File pipelineScriptFile = new File(gitRepoRule.getRoot(), "Jenkinsfile");
        FileUtils.write(pipelineScriptFile, pipelineScript, StandardCharsets.UTF_8);

        gitRepoRule.git("add", "--all");
        gitRepoRule.git("commit", "--message=commit");
        project.setDefinition(new CpsScmFlowDefinition(new GitSCM(gitRepoRule.toString()), "Jenkinsfile"));

        return project;
    }

    private String fileCreationCode(String folder, String file) {
        return "sh '''" + fileCreationCodeForLinux(folder, file + ".sh") + "'''";
    }

    private String fileCreationCodeForLinux(String folder, String file) {
        String filePath = folder + "/" + file;
        return ""
                + "set +x\n"
                + "[ -f '" + filePath + "' ] && '" + filePath + "'\n"
                + "mkdir -p '" + folder + "'\n"
                + "echo echo expected output from test file > '" + filePath + "'\n"
                + "chmod a+x '" + filePath + "'\n";
    }

}
