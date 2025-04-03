package jenkins.plugins.itemstorage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import jenkins.plugins.itemstorage.local.LocalItemStorage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class DataMigrationTest {

    @Test
    @LocalData
    void shouldMigrateLocalData(JenkinsRule jenkins) {
        ItemStorage<?> storage = GlobalItemStorage.get().getStorage();
        assertThat(storage, is(notNullValue()));
        LocalItemStorage localItemStorage = (LocalItemStorage) storage;
        assertThat(localItemStorage.getRoot(), is("jobcaches"));
    }
}
