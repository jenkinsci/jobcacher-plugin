package jenkins.plugins.jobcacher.arbitrary;

import hudson.FilePath;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class FilenameSpaceTest {

    @Test
    void shouldFailWhenFilenameContainsSpaces() throws Exception {

        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test space dir");
        tempDir.mkdirs();

        File file = new File(tempDir, "file with space.txt");
        file.createNewFile();

        FilePath source = new FilePath(tempDir);
        FilePath target = new FilePath(File.createTempFile("out", ".zip"));

        ZipArbitraryFileCacheStrategy strategy = new ZipArbitraryFileCacheStrategy();

        assertThrows(Exception.class, () ->
                strategy.compress(source, "**/*", null, true, target)
        );
    }
}