package jenkins.plugins.jobcacher;

import java.util.UUID;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.*;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;

import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.s3.NonAWSS3ItemStorage;
import minio.MinioContainer;
import minio.MinioMcContainer;

@Testcontainers(disabledWithoutDocker = true)
@WithJenkins
public class ArbitraryFileCacheStepMinioTest {

    @Container
    public static MinioContainer minio = new MinioContainer();

    @Container
    public static MinioMcContainer mc = new MinioMcContainer(minio);

    public void setupCache(JenkinsRule j) throws Exception {
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
                "instances1/",
                minio.getExternalAddress(),
                "us-west-1",
                null,
                true,
                false
        );
        GlobalItemStorage.get().setStorage(storage);
    }

    @Test
    public void testBackupAndRestore(JenkinsRule j) throws Exception {

        setupCache(j);

        j.jenkins.setNumExecutors(0);
        j.createSlave(true);

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
                "  cache(skipSave: true, maxCacheSize: 250, caches: [arbitraryFileCache(path: 'sub', compressionMethod: 'TARGZ')]){\n" +
                "    sh 'rm sub/file'\n" +
                "  }\n" +
                "}", true));

        // WHEN
        result = job.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(result);

        // THEN
        j.assertBuildStatusSuccess(result);
        j.assertLogContains("Skipping save due to skipSave being set to true.", result);

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