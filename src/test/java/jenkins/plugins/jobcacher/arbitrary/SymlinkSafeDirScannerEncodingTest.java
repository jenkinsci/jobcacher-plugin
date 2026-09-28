package jenkins.plugins.jobcacher.arbitrary;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reproduces <a href="https://github.com/jenkinsci/jobcacher-plugin/issues/534">issue #534</a>: on an
 * agent whose JVM was started without a UTF-8 locale (so {@code sun.jnu.encoding} is e.g.
 * {@code ANSI_X3.4-1968}), a cached directory containing a file whose name is not valid in that encoding
 * (e.g. produced by a tool running under a different locale) made the whole cache save fail with
 * {@code java.nio.file.InvalidPathException: Malformed input or input contains unmappable characters}.
 *
 * <p>Reproducing the actual JVM/locale condition requires a real forked JVM started with
 * {@code LANG=C}/{@code LC_ALL=C} (see {@link EncodingReproMain}), since {@code sun.jnu.encoding} is fixed
 * at JVM startup and cannot be overridden on the running test JVM.
 */
class SymlinkSafeDirScannerEncodingTest {

    @Test
    void skipsFilesThatCannotBeEncodedInThePlatformCharsetInsteadOfFailingTheWholeScan(@TempDir Path dir)
            throws Exception {
        Files.writeString(dir.resolve("readable.txt"), "hello");
        createFileWithInvalidUtf8Name(dir, "broken.txt");

        String output = runInForkedJvmWithNonUtf8Locale(dir);
        String resultLine = output.lines()
                .filter(l -> l.startsWith("RESULT:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no RESULT line in output: " + output));

        assertThat(
                "scanning must not fail the whole cache operation just because one file name is not"
                        + " representable in the platform encoding: " + output,
                resultLine,
                containsString("RESULT:OK:"));
        assertThat(
                "the file with a name that round-trips losslessly must still be cached",
                resultLine,
                containsString("readable.txt"));
        assertThat(
                "the file whose name cannot be represented under the platform encoding must be skipped,"
                        + " not silently corrupted or included under a mangled name",
                resultLine.contains("broken") || resultLine.contains("?"),
                equalTo(false));
    }

    /**
     * Creates a file whose name contains a raw byte sequence ({@code 0xFF 0xFE}) that is not valid in
     * UTF-8 (or ASCII), by shelling out to {@code touch} with an ANSI-C-quoted literal. This bypasses
     * Java's own file name encoding entirely, so the malformed name lands on disk regardless of the
     * encoding this test JVM itself was started with.
     */
    private static void createFileWithInvalidUtf8Name(Path dir, String suffix) throws IOException, InterruptedException {
        Process touch = new ProcessBuilder(
                        "bash", "-c", "cd \"$1\" && touch $'\\xff\\xfe" + suffix + "'", "--", dir.toString())
                .redirectErrorStream(true)
                .start();
        assumeTrue(touch.waitFor() == 0, "could not create a file with an invalid-encoding name via `touch`");
    }

    /**
     * Runs {@link EncodingReproMain} in a forked JVM with {@code LANG=C}/{@code LC_ALL=C} so that
     * {@code sun.jnu.encoding} is a non-Unicode charset, matching the misconfigured-locale agents where
     * this bug was reported.
     */
    private static String runInForkedJvmWithNonUtf8Locale(Path dir) throws IOException, InterruptedException {
        String javaBin = System.getProperty("java.home") + "/bin/java";
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-cp", classpath, EncodingReproMain.class.getName(), dir.toString());
        pb.environment().put("LANG", "C");
        pb.environment().put("LC_ALL", "C");
        pb.redirectErrorStream(true);

        Process process = pb.start();
        List<String> lines;
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        }
        int exitCode = process.waitFor();
        String output = String.join("\n", lines);

        assumeTrue(exitCode == 0, "forked JVM exited abnormally, output was: " + output);

        String encodingLine =
                lines.stream().filter(l -> l.startsWith("ENCODING:")).findFirst().orElse("");
        String encoding = encodingLine.substring("ENCODING:".length());
        assumeTrue(
                !encoding.toUpperCase(Locale.ROOT).contains("UTF"),
                "could not force a non-UTF-8 sun.jnu.encoding via LANG=C/LC_ALL=C on this system"
                        + " (got '" + encoding + "'), skipping reproduction");

        return output;
    }
}
