package jenkins.plugins.itemstorage.local;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import java.io.File;
import java.io.IOException;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class LocalItemStorageTest {

    @TempDir
    private File tempDir;

    @Test
    void testConfigRoundTrip(JenkinsRule jenkins) throws Exception {
        assertThat(storage().getRoot(), nullValue());
        storage().setRoot("custom-root");

        jenkins.configRoundtrip();

        assertThat(storage().getRoot(), is("custom-root"));
    }

    @Test
    void testCustomRootHandling(JenkinsRule jenkins) throws IOException, InterruptedException {
        storage().setRoot(tempDir.getAbsolutePath());

        FreeStyleProject project = jenkins.createFreeStyleProject("project");
        File cacheDir = cacheDir(project);
        assertThat(cacheDir.getAbsolutePath(), is(tempDirPath() + "/project/cache"));

        assertThat(cacheDir.mkdirs(), is(true));
        assertThat(cacheDir.isDirectory(), is(true));

        project.renameTo("renamed-project");
        cacheDir = cacheDir(project);
        assertThat(cacheDir.getAbsolutePath(), is(tempDirPath() + "/renamed-project/cache"));
        assertThat(cacheDir.isDirectory(), is(true));

        project.delete();
        assertThat(cacheDir.exists(), is(false));
    }

    private LocalItemStorage storage() {
        return (LocalItemStorage) GlobalItemStorage.get().getStorage();
    }

    private String tempDirPath() {
        return tempDir.getAbsolutePath();
    }

    private File cacheDir(Item item) {
        return new File(storage().getObjectPath(item, "cache").getPath());
    }
}
