package jenkins.plugins.itemstorage.local;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;

public class LocalItemStorageTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void testConfigRoundTrip() throws Exception {
        assertThat(storage().getRoot(), nullValue());
        storage().setRoot("custom-root");

        jenkins.configRoundtrip();

        assertThat(storage().getRoot(), is("custom-root"));
    }

    @Test
    public void testCustomRootHandling() throws IOException, InterruptedException {
        storage().setRoot(tempDir.getRoot().getAbsolutePath());

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
        return tempDir.getRoot().getAbsolutePath();
    }

    private File cacheDir(Item item) {
        return new File(storage().getObjectPath(item, "cache").getPath());
    }

}
