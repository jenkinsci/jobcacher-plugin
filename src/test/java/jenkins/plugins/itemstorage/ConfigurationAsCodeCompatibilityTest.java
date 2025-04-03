package jenkins.plugins.itemstorage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import jenkins.plugins.itemstorage.local.LocalItemStorage;
import org.junit.jupiter.api.Test;

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
}
