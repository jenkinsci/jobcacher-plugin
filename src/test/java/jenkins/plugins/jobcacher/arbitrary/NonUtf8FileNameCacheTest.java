package jenkins.plugins.jobcacher.arbitrary;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.plugins.itemstorage.local.LocalObjectPath;
import jenkins.plugins.jobcacher.ArbitraryFileCache.CompressionMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Reproduces <a href="https://github.com/jenkinsci/jobcacher-plugin/issues/246">#246</a>.
 *
 * <p>A file such as {@code nvue - 副本.js} (shipped by the {@code uview-plus} npm package) can only be accessed by
 * name through {@link File} when the JVM's file name encoding ({@code sun.jnu.encoding}) can represent it. On an agent
 * started without a UTF-8 locale (e.g. a container where {@code LANG} is unset, so the JVM uses ANSI_X3.4-1968), the
 * file name is decoded lossily and every later {@link File#toPath()} throws
 * {@code InvalidPathException: Malformed input or input contains unmappable characters}, which made the whole cache
 * creation fail.
 *
 * <p>{@code sun.jnu.encoding} is derived from the locale at JVM startup and cannot be overridden with a system
 * property, so the cache is created in a child JVM started with {@code LC_ALL=C}.
 */
class NonUtf8FileNameCacheTest {

    private static final String NON_ASCII_FILE = "nvue - 副本.js";
    private static final String CACHED_DIR = "node_modules/uview-plus/components/u-slider";

    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @EnumSource(
            value = CompressionMethod.class,
            names = {"ZIP", "TARGZ", "TAR_ZSTD"})
    void cacheIsCreatedAndRestoredWhenAgentCannotEncodeFileName(CompressionMethod compressionMethod) throws Exception {
        Path source = tempDir.resolve("source");
        Path dir = Files.createDirectories(source.resolve(CACHED_DIR));
        Files.writeString(dir.resolve("nvue.js"), "regular");
        Files.writeString(dir.resolve(NON_ASCII_FILE), "copy");

        ChildResult result = runInNonUtf8Jvm(compressionMethod, source, tempDir.resolve("restored"));

        assertThat(result.output, result.exitCode, is(0));
        assertThat(result.output, containsString("Skipped 1 file"));
        assertThat(
                Files.readString(tempDir.resolve("restored").resolve(CACHED_DIR).resolve("nvue.js")), is("regular"));
    }

    @Test
    void nonAsciiFileNamesAreCachedWhenAgentUsesUtf8() throws Exception {
        assumeTrue(
                "UTF-8".equalsIgnoreCase(System.getProperty("sun.jnu.encoding")),
                "this JVM does not use UTF-8 for file names");
        Path source = tempDir.resolve("source");
        Path dir = Files.createDirectories(source.resolve(CACHED_DIR));
        Files.writeString(dir.resolve(NON_ASCII_FILE), "copy");
        Path restored = tempDir.resolve("restored");
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));

        cacheAndRestore(
                CompressionMethod.TARGZ, source, restored, workspace, tempDir.resolve("storage"), TaskListener.NULL);

        assertThat(Files.readString(restored.resolve(CACHED_DIR).resolve(NON_ASCII_FILE)), is("copy"));
    }

    private ChildResult runInNonUtf8Jvm(CompressionMethod compressionMethod, Path source, Path restored)
            throws IOException, InterruptedException {
        assumeFalse(Functions.isWindows(), "file name encoding is not locale dependent on Windows");

        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Child.class.getName());
        command.add(compressionMethod.name());
        command.add(source.toString());
        command.add(restored.toString());
        command.add(Files.createDirectories(tempDir.resolve("workspace")).toString());
        command.add(tempDir.resolve("storage").toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
        processBuilder.environment().keySet().removeIf(key -> key.equals("LANG") || key.startsWith("LC_"));
        processBuilder.environment().put("LC_ALL", "C");
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(output, process.waitFor(2, TimeUnit.MINUTES), is(true));

        assumeFalse(output.contains(Child.UTF8_JNU_ENCODING), "LC_ALL=C does not change the file name encoding here");
        return new ChildResult(process.exitValue(), output);
    }

    private static void cacheAndRestore(
            CompressionMethod compressionMethod,
            Path source,
            Path restored,
            Path workspace,
            Path storage,
            TaskListener listener)
            throws IOException, InterruptedException {
        ArbitraryFileCacheStrategy strategy = compressionMethod.getCacheStrategy();
        LocalObjectPath cache = new LocalObjectPath(
                new FilePath(storage.resolve(strategy.createCacheName("cache")).toFile()));
        FilePath workspacePath = new FilePath(workspace.toFile());

        strategy.cache(new FilePath(source.toFile()), "**/*", null, true, cache, workspacePath, listener);
        strategy.restore(cache, new FilePath(restored.toFile()), workspacePath);
    }

    private record ChildResult(int exitCode, String output) {}

    /** Entry point of the child JVM which is started without a UTF-8 locale. */
    public static final class Child {

        static final String UTF8_JNU_ENCODING = "file name encoding is UTF-8";

        public static void main(String[] args) throws Exception {
            if ("UTF-8".equalsIgnoreCase(System.getProperty("sun.jnu.encoding"))) {
                System.out.println(UTF8_JNU_ENCODING);
                System.exit(0);
            }
            cacheAndRestore(
                    CompressionMethod.valueOf(args[0]),
                    Path.of(args[1]),
                    Path.of(args[2]),
                    Path.of(args[3]),
                    Path.of(args[4]),
                    StreamTaskListener.fromStdout());
        }
    }
}
