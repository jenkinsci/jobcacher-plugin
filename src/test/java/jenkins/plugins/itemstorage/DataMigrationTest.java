package jenkins.plugins.itemstorage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import jenkins.plugins.itemstorage.local.LocalItemStorage;
import jenkins.plugins.itemstorage.s3.NonAWSS3ItemStorage;
import jenkins.plugins.itemstorage.s3.S3ItemStorage;

public class DataMigrationTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    @LocalData
    public void shouldMigrateLocalData() {
        ItemStorage<?> storage = GlobalItemStorage.get().getStorage();
        assertThat(storage, is(notNullValue()));
        LocalItemStorage localItemStorage = (LocalItemStorage) storage;
        assertThat(localItemStorage.getRoot(), is("jobcaches"));
    }

    @Test
    @LocalData
    public void shouldMigrateAwsData() {
        ItemStorage<?> storage = GlobalItemStorage.get().getStorage();
        assertThat(storage, is(notNullValue()));
        S3ItemStorage s3ItemStorage = (S3ItemStorage) storage;
        assertThat(s3ItemStorage.getBucketName(), is("bucket1"));
        assertThat(s3ItemStorage.getRegion(), is("us-gov-west-1"));
        assertThat(s3ItemStorage.getCredentialsId(), is("s3"));
        assertThat(s3ItemStorage.getPrefix(), is("the-prefix/"));
    }

    @Test
    @LocalData
    public void shouldMigrateNonAwsData() {
        ItemStorage<?> storage = GlobalItemStorage.get().getStorage();
        assertThat(storage, is(notNullValue()));
        NonAWSS3ItemStorage s3ItemStorage = (NonAWSS3ItemStorage) storage;
        assertThat(s3ItemStorage.getBucketName(), is("bucket1"));
        assertThat(s3ItemStorage.getRegion(), is("eu-central-2"));
        assertThat(s3ItemStorage.getCredentialsId(), is("s3"));
        assertThat(s3ItemStorage.getPrefix(), is("the-prefix/"));
        assertThat(s3ItemStorage.getEndpoint(), is("http://localhost:9000"));
        assertThat(s3ItemStorage.getParallelDownloads(), is(true));
        assertThat(s3ItemStorage.getPathStyleAccess(), is(true));
    }

}
