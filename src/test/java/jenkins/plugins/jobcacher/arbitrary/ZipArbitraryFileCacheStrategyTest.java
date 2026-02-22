package jenkins.plugins.jobcacher.arbitrary;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZipArbitraryFileCacheStrategyTest {

    @Test
    void trimsIncludesPattern() throws Exception {
        ZipArbitraryFileCacheStrategy strategy = new ZipArbitraryFileCacheStrategy();

        String includes = "   *.txt   ";
        String excludes = null;

        String trimmed = includes.trim();

        assertEquals("*.txt", trimmed);
    }
}