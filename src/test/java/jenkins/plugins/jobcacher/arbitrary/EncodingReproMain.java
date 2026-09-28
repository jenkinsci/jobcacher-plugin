package jenkins.plugins.jobcacher.arbitrary;

import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import java.io.File;
import java.io.IOException;
import java.util.TreeSet;

/**
 * Standalone entry point run in a forked JVM (with {@code LANG=C}/{@code LC_ALL=C}, so that
 * {@code sun.jnu.encoding} is a non-Unicode charset) by {@link SymlinkSafeDirScannerEncodingTest} to
 * reproduce and verify the fix for
 * <a href="https://github.com/jenkinsci/jobcacher-plugin/issues/534">issue #534</a>: scanning a directory
 * containing a file name with bytes that are not valid in the platform encoding used to crash the whole
 * scan with an unrecoverable {@link java.nio.file.InvalidPathException}.
 *
 * <p>Reproducing this requires an actual forked JVM started with a non-UTF-8 locale, since
 * {@code sun.jnu.encoding} is fixed at JVM startup from the OS locale and cannot be changed at runtime.
 *
 * <p>Prints {@code RESULT:OK:<comma-separated sorted relative paths visited>} on success, or
 * {@code RESULT:FAIL:<exception class>:<message>} if scanning throws.
 */
public final class EncodingReproMain {

    private EncodingReproMain() {}

    public static void main(String[] args) {
        System.out.println("ENCODING:" + System.getProperty("sun.jnu.encoding"));

        File dir = new File(args[0]);
        DirScanner scanner = new SymlinkSafeDirScanner("**", null, true);
        TreeSet<String> visited = new TreeSet<>();

        try {
            scanner.scan(dir, new FileVisitor() {
                @Override
                public void visit(File f, String relativePath) throws IOException {
                    visited.add(relativePath);
                }

                @Override
                public boolean understandsSymlink() {
                    return true;
                }
            });
        } catch (Throwable t) {
            System.out.println("RESULT:FAIL:" + t.getClass().getName() + ":" + t.getMessage());
            return;
        }

        System.out.println("RESULT:OK:" + String.join(",", visited));
    }
}
