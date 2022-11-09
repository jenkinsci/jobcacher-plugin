package jenkins.plugins.jobcacher;

import java.util.UUID;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;

import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.s3.NonAWSS3ItemStorage;
import minio.MinioContainer;
import minio.MinioMcContainer;

/**
 * Checks that the cache step works as expected in pipelines. Each test starts with an empty bucket.
 */
public class ArbitraryFileCacheStepMinioTest {

    @ClassRule
    public static MinioContainer minio = new MinioContainer();

    @ClassRule
    public static MinioMcContainer mc = new MinioMcContainer(minio);

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @BeforeClass
    public static void setupJenkins() throws Exception {
        // execute build jobs on the agent node only
        j.jenkins.setNumExecutors(0);
        j.createSlave(true);
    }

    @Before
    public void setupCache() throws Exception {
        // create a test bucket in MinIO
        String bucket = UUID.randomUUID().toString();
        mc.createBucket(bucket);

        // setup credentials for bucket in Jenkins
        AWSCredentialsImpl credentials = new AWSCredentialsImpl(
                CredentialsScope.SYSTEM,
                "minio-test-credentials-id",
                minio.accessKey(),
                minio.secretKey(),
                "minio test credentials");
        SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
        SystemCredentialsProvider.getInstance().save();

        // configure the corresponding ItemStorage in Jenkins
        NonAWSS3ItemStorage storage = new NonAWSS3ItemStorage(
                "minio-test-credentials-id",
                bucket,
                minio.getExternalAddress(),
                "us-west-1",
                null,
                true,
                false
        );
        GlobalItemStorage.get().setStorage(storage);
    }

    @Test
    public void testBackupAndRestore() throws Exception {
        // GIVEN
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("node {\n" +
                "  cache(maxCacheSize: 250, caches: [arbitraryFileCache(path: 'sub', compressionMethod: 'TARGZ')]){\n" +
                "    sh 'mkdir sub'\n" +
                "    sh 'echo sub-content > sub/file'\n" +
                "  }\n" +
                "}", true));

        // WHEN
        WorkflowRun result = job.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(result);

        // THEN
        j.assertBuildStatusSuccess(result);
        j.assertLogContains("Skip restoring cache as no up-to-date cache exists", result);
        j.assertLogContains("Creating cache...", result);

        // GIVEN
        job.setDefinition(new CpsFlowDefinition("node {\n" +
                "  sh 'rm -rf *'\n" +
                "  cache(maxCacheSize: 250, caches: [arbitraryFileCache(path: 'sub', compressionMethod: 'TARGZ')]){\n" +
                "    sh 'cat sub/file'\n" +
                "  }\n" +
                "}", true));

        // WHEN
        result = job.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(result);

        // THEN
        j.assertBuildStatusSuccess(result);
        j.assertLogContains("sub-content", result);
    }

}
