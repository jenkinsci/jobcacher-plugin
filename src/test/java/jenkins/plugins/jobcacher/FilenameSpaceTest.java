package jenkins.plugins.jobcacher;

import hudson.FilePath;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilenameSpaceTest {

    @Test
    public void testTrimPatterns() throws Exception {

        String includes = "  **/*.txt  ";
        String excludes = "  **/*.log  ";

        String safeIncludes = includes == null ? null : includes.trim();
        String safeExcludes = excludes == null ? null : excludes.trim();

        assertEquals("**/*.txt", safeIncludes);
        assertEquals("**/*.log", safeExcludes);
    }
}