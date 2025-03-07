package jenkins.plugins.itemstorage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import org.junit.jupiter.api.Test;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import jenkins.plugins.itemstorage.local.LocalItemStorage;
import jenkins.plugins.itemstorage.s3.NonAWSS3ItemStorage;
import jenkins.plugins.itemstorage.s3.S3ItemStorage;

@WithJenkinsConfiguredWithCode
class ConfigurationAsCodeCompatibilityTest {

    @Test
    @ConfiguredWithCode("local.yml")
    void shouldSupportConfigurationAsCodeForLocalStorage(JenkinsConfiguredWithCodeRule jenkins) {
        ItemStorage<?> storage = GlobalItemStorage.get().getStorage();
        assertThat(storage, is(notNullValue()));
        LocalItemStorage localItemStorage = (LocalItemStorage) storage;
        assertThat(localItemStorage.getRoot(), is("jobcaches"));
    }

    @Test
    @ConfiguredWithCode("nonaws.yml")
    void shouldSupportConfigurationAsCodeForNonAws(JenkinsConfiguredWithCodeRule jenkins) {
        ItemStorage<?> storage = GlobalItemStorage.get().getStorage();
        assertThat(storage, is(notNullValue()));
        NonAWSS3ItemStorage s3ItemStorage = (NonAWSS3ItemStorage) storage;
        assertThat(s3ItemStorage.getBucketName(), is("caches"));
        assertThat(s3ItemStorage.getRegion(), is("eu-central-2"));
        assertThat(s3ItemStorage.getCredentialsId(), is("s3"));
        assertThat(s3ItemStorage.getPrefix(), is("the-prefix/"));
        assertThat(s3ItemStorage.getEndpoint(), is("http://localhost:9000"));
        assertThat(s3ItemStorage.getParallelDownloads(), is(false));
        assertThat(s3ItemStorage.getPathStyleAccess(), is(true));
    }

    @Test
    @ConfiguredWithCode("aws.yml")
    void shouldSupportConfigurationAsCodeForAws(JenkinsConfiguredWithCodeRule jenkins) {
        ItemStorage<?> storage = GlobalItemStorage.get().getStorage();
        assertThat(storage, is(notNullValue()));
        S3ItemStorage s3ItemStorage = (S3ItemStorage) storage;
        assertThat(s3ItemStorage.getBucketName(), is("caches"));
        assertThat(s3ItemStorage.getRegion(), is("eu-central-2"));
        assertThat(s3ItemStorage.getCredentialsId(), is("s3"));
        assertThat(s3ItemStorage.getPrefix(), is("the-prefix/"));
    }

}
