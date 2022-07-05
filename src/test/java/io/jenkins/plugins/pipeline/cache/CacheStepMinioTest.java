package io.jenkins.plugins.pipeline.cache;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

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

import hudson.model.Result;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.s3.NonAWSS3ItemStorage;

/**
 * Checks that the cache step works as expected in pipelines. Each test starts with an empty bucket.
 */
public class CacheStepMinioTest {

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
                true
        );
        GlobalItemStorage.get().setStorage(storage);
    }

    @Test
    public void testBackupAndRestore() throws Exception {
        // GIVEN
        WorkflowJob jobA = createJob("node {\n" +
                "  cache(path: '.', key: '1234') {\n" +
                "    writeFile text: 'red-content', file: 'file'\n" +
                "    dir ('sub') {\n" +
                "      writeFile text: 'blue-content', file: 'file'\n" +
                "    }\n" +
                "  }\n" +
                "}");
        WorkflowJob jobB = createJob("node {\n" +
                "  cache(path: '.', key: '1234') {\n" +
                "    println(readFile(file: 'file'))\n" +
                "    dir ('sub') {\n" +
                "      println(readFile(file: 'file'))\n" +
                "    }\n" +
                "  }\n" +
                "}");

        // WHEN
        WorkflowRun resultA = executeJob(jobA);
        WorkflowRun resultB = executeJob(jobB);

        // THEN
        j.assertBuildStatusSuccess(resultA);
        j.assertLogContains("Cache not restored (no such key found)", resultA);
        j.assertLogContains("Cache saved successfully (1234)", resultA);
        j.assertBuildStatusSuccess(resultB);
        j.assertLogContains("Cache restored successfully (1234)", resultB);
        j.assertLogContains("red-content", resultB);
        j.assertLogContains("blue-content", resultB);
        j.assertLogContains("Cache not saved (1234 already exists)", resultB);
    }

    @Test
    public void testBackupIsSkippedOnError() throws Exception {
        // GIVEN
        WorkflowJob job = createJob("node {\n" +
                "  cache(path: '.', key: 'a') {\n" +
                "    error 'Program failed, please read logs...'\n" +
                "  }\n" +
                "}");

        // WHEN
        WorkflowRun result = executeJob(job);

        // THEN
        j.assertBuildStatus(Result.FAILURE, result);
        j.assertLogContains("Cache not saved (inner-step execution failed)", result);
    }

    @Test
    public void testRestoreKey() throws Exception {
        // GIVEN
        WorkflowJob jobA = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-a') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobB = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-b', restoreKeys: ['cache-a']) {}\n" +
                "}");

        // WHEN
        WorkflowRun resultA = executeJob(jobA);
        WorkflowRun resultB = executeJob(jobB);

        // THEN
        j.assertBuildStatusSuccess(resultA);
        j.assertBuildStatusSuccess(resultB);
        j.assertLogContains("Cache restored successfully (cache-a)", resultB);
        j.assertLogContains("Cache saved successfully (cache-b)", resultB);
    }

    @Test
    public void testRestoreKeyNotFound() throws Exception {
        // GIVEN
        WorkflowJob job = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-b', restoreKeys: ['cache-a']) {\n" +
                "    writeFile file: 'file', text: 'bla'\n" +
                "  }\n" +
                "}");

        // WHEN
        WorkflowRun result = executeJob(job);

        // THEN
        j.assertBuildStatusSuccess(result);
        j.assertLogContains("Cache not restored (no such key found)", result);
        j.assertLogContains("Cache saved successfully (cache-b)", result);
    }

    @Test
    public void testRestoreKeyPrefix() throws Exception {
        // GIVEN
        WorkflowJob jobA = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-a') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobB = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-b', restoreKeys: ['cac']) {}\n" +
                "}");

        // WHEN
        WorkflowRun resultA = executeJob(jobA);
        WorkflowRun resultB = executeJob(jobB);

        // THEN
        j.assertBuildStatusSuccess(resultA);
        j.assertLogContains("Cache not restored (no such key found)", resultA);
        j.assertLogContains("Cache saved successfully (cache-a)", resultA);
        j.assertBuildStatusSuccess(resultB);
        j.assertLogContains("Cache restored successfully (cache-a)", resultB);
        j.assertLogContains("Cache saved successfully (cache-b)", resultB);
    }

    @Test
    public void testRestoreKeyFirstOneWins() throws Exception {
        // GIVEN
        WorkflowJob jobA = createJob("node {\n" +
                "  cache(path: '.', key: 'a') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobB = createJob("node {\n" +
                "  cache(path: '.', key: 'b') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobC = createJob("node {\n" +
                "  cache(path: '.', key: 'c') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobD = createJob("node {\n" +
                "  cache(path: '.', key: 'd', restoreKeys: ['b','a','c']) {}\n" +
                "}");

        // WHEN
        WorkflowRun resultA = executeJob(jobA);
        WorkflowRun resultB = executeJob(jobB);
        WorkflowRun resultC = executeJob(jobC);
        WorkflowRun resultD = executeJob(jobD);

        // THEN
        j.assertBuildStatusSuccess(resultA);
        j.assertBuildStatusSuccess(resultB);
        j.assertBuildStatusSuccess(resultC);
        j.assertBuildStatusSuccess(resultD);
        j.assertLogContains("Cache restored successfully (b)", resultD);
    }

    @Test
    public void testRestoreKeyExactMatchWins() throws Exception {
        // GIVEN
        WorkflowJob jobA = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-a') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobB = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-b') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobC = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-c') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobD = createJob("node {\n" +
                "  cache(path: '.', key: 'cache', restoreKeys: ['cache-','cache-b']) {}\n" +
                "}");

        // WHEN
        WorkflowRun resultA = executeJob(jobA);
        WorkflowRun resultB = executeJob(jobB);
        WorkflowRun resultC = executeJob(jobC);
        WorkflowRun resultD = executeJob(jobD);

        // THEN
        j.assertBuildStatusSuccess(resultA);
        j.assertBuildStatusSuccess(resultB);
        j.assertBuildStatusSuccess(resultC);
        j.assertBuildStatusSuccess(resultD);
        j.assertLogContains("Cache restored successfully (cache-b)", resultD);
    }

    @Test
    public void testRestoreKeyLatestOneWins() throws Exception {
        // GIVEN
        WorkflowJob jobA = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-3') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobB = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-1') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobC = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-2') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobD = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-4', restoreKeys: ['cache-']) {}\n" +
                "}");

        // WHEN
        WorkflowRun resultA = executeJob(jobA);
        WorkflowRun resultB = executeJob(jobB);
        WorkflowRun resultC = executeJob(jobC);
        WorkflowRun resultD = executeJob(jobD);

        // THEN
        j.assertBuildStatusSuccess(resultA);
        j.assertBuildStatusSuccess(resultB);
        j.assertBuildStatusSuccess(resultC);
        j.assertBuildStatusSuccess(resultD);
        j.assertLogContains("Cache restored successfully (cache-2)", resultD);
    }

    @Test
    public void testKeyIsPreferredOverRestoreKey() throws Exception {
        // GIVEN
        WorkflowJob jobA = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-a') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobB = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-b') {\n" +
                "    writeFile text: 'my-content', file: 'file'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobC = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-a', restoreKeys: ['cache-b']) {}\n" +
                "}");

        // WHEN
        WorkflowRun resultA = executeJob(jobA);
        WorkflowRun resultB = executeJob(jobB);
        WorkflowRun resultC = executeJob(jobC);

        // THEN
        j.assertBuildStatusSuccess(resultA);
        j.assertBuildStatusSuccess(resultB);
        j.assertBuildStatusSuccess(resultC);
        j.assertLogContains("Cache restored successfully (cache-a)", resultC);
    }

    @Test
    public void testHashFiles() throws Exception {
        // GIVEN
        WorkflowJob job = createJob("node {\n" +
                "  writeFile text: 'v1', file: 'pom.xml'\n" +
                "  cache(path: '.', key: \"cache-${hashFiles('**/pom.xml')}\") {}\n" +
                "}");

        // WHEN
        WorkflowRun result = executeJob(job);

        // THEN
        j.assertBuildStatusSuccess(result);
        j.assertLogContains("Cache not restored (no such key found)", result);
        j.assertLogContains("Cache saved successfully (cache-6654c734ccab8f440ff0825eb443dc7f)", result);
    }

    @Test
    public void testHashFilesEmptyResult() throws Exception {
        // GIVEN
        WorkflowJob job = createJob("node {\n" +
                "  writeFile text: 'my-content', file: 'file'\n" +
                "  cache(path: '.', key: \"cache-${hashFiles('**/pom.xml')}\") {}\n" +
                "}");

        // WHEN
        WorkflowRun result = executeJob(job);

        // THEN
        j.assertBuildStatusSuccess(result);
        j.assertLogContains("Cache not restored (no such key found)", result);
        j.assertLogContains("Cache saved successfully (cache-d41d8cd98f00b204e9800998ecf8427e)", result);
    }

    @Test
    public void testPathNotExists() throws Exception {
        // GIVEN
        WorkflowJob job = createJob("node {\n" +
                "  cache(path: '.', key: 'a') {}\n" +
                "}");

        // WHEN
        WorkflowRun result = executeJob(job);

        // THEN
        j.assertBuildStatusSuccess(result);
        j.assertLogContains("Cache not restored (no such key found)", result);
        j.assertLogContains("Cache not saved (path not exists)", result);
    }

    @Test
    public void testPathIsFile() throws Exception {
        // GIVEN
        WorkflowJob job = createJob("node {\n" +
                "  writeFile file: 'file', text: 'bla'\n" +
                "  cache(path: 'file', key: 'a') {}\n" +
                "}");

        // WHEN
        WorkflowRun result = executeJob(job);

        // THEN
        j.assertBuildStatusSuccess(result);
        j.assertLogContains("Cache not restored (path is not a directory)", result);
        j.assertLogContains("Cache not saved (path is not a directory)", result);
    }

    @Test
    public void testPathFilter() throws Exception {
        // GIVEN
        WorkflowJob jobA = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-a', filter: '**/*.json') {\n" +
                "    writeFile text: 'json content', file: 'file.json'\n" +
                "    writeFile text: 'xml content', file: 'file.xml'\n" +
                "  }\n" +
                "}");
        WorkflowJob jobB = createJob("node {\n" +
                "  cache(path: '.', key: 'cache-a') {\n" +
                "    if (fileExists('file.json')) { echo 'file.json_exists' }\n" +
                "    if (fileExists('file.xml')) { echo 'file.xml_exists' }\n" +
                "  }\n" +
                "}");

        // WHEN
        WorkflowRun resultA = executeJob(jobA);
        WorkflowRun resultB = executeJob(jobB);

        // THEN
        j.assertBuildStatusSuccess(resultA);
        j.assertBuildStatusSuccess(resultB);
        j.assertLogContains("file.json_exists", resultB);
        j.assertLogNotContains("file.xml_exists", resultB);
    }

    private WorkflowRun executeJob(WorkflowJob job) throws InterruptedException, ExecutionException {
        WorkflowRun result = job.scheduleBuild2(0).waitForStart();
        j.waitForCompletion(result);

        return result;
    }

    private WorkflowJob createJob(String script) throws IOException {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition(script, true));

        return job;
    }
}
