package jenkins.plugins.itemstorage.local;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalItemStorageTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void testConfigRoundTrip() throws Exception {
        assertThat(storage().getRoot()).isNull();
        storage().setRoot("custom-root");

        jenkins.configRoundtrip();

        assertThat(storage().getRoot()).isEqualTo("custom-root");
    }

    @Test
    public void testCustomRootHandling() throws IOException, InterruptedException {
        storage().setRoot(tempDir.getRoot().getAbsolutePath());

        FreeStyleProject project = jenkins.createFreeStyleProject("project");
        File cacheDir = cacheDir(project);
        assertThat(cacheDir.getAbsolutePath()).isEqualTo(tempDirPath() + "/project/cache");

        assertThat(cacheDir.mkdirs()).isTrue();
        assertThat(cacheDir).isDirectory();

        project.renameTo("renamed-project");
        cacheDir = cacheDir(project);
        assertThat(cacheDir.getAbsolutePath()).isEqualTo(tempDirPath() + "/renamed-project/cache");
        assertThat(cacheDir).isDirectory();

        project.delete();
        assertThat(cacheDir).doesNotExist();
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
