package jenkins.plugins.jobcacher.arbitrary;

import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.selectors.SelectorUtils;

/**
 * A directory scanner that archives the directory as-is, preserving symlinks as native entries
 * when the archiver supports them (e.g., TAR).
 *
 * <p>This replaces the blanket symlink-skipping behavior of {@code LinkOption.NOFOLLOW_LINKS}
 * passed to {@code FilePath.archive()}, which strips all symlinks including ones needed by
 * tools like npm/yarn (e.g., {@code node_modules/.bin} symlinks).
 *
 * <p>The scanning strategy: walk the directory tree with {@link Files#walkFileTree}, without
 * following symlinks, and forward each entry matching the includes/excludes pattern to the
 * archiver via {@code scanSingle()} &mdash; as a native symlink entry if it is one and the
 * archiver supports them, as a regular file otherwise.
 *
 * <p>The walk is done with NIO2 {@link Path}s throughout, and only converted to a {@link File}
 * right before handing it to the archiver. This matters because {@code java.io.File}/{@code String}
 * can only represent a file name that round-trips losslessly through the JVM's platform
 * encoding ({@code sun.jnu.encoding}); Ant's own {@code DirectoryScanner} (previously used here)
 * performs that lossy round-trip internally while checking for symlinks, and throws an
 * unrecoverable {@link java.nio.file.InvalidPathException} for a file name containing bytes that
 * aren't valid in that encoding (e.g. non-ASCII bytes on an agent without a UTF-8 locale
 * configured), aborting the whole cache save. Here such a file is instead skipped with a
 * warning, since it cannot be represented as a {@link File} correctly at all.
 */
class SymlinkSafeDirScanner extends DirScanner {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(SymlinkSafeDirScanner.class.getName());

    private final String includes;
    private final String excludes;
    private final boolean useDefaultExcludes;

    SymlinkSafeDirScanner(String includes, String excludes, boolean useDefaultExcludes) {
        this.includes = includes;
        this.excludes = excludes;
        this.useDefaultExcludes = useDefaultExcludes;
    }

    @Override
    public void scan(File dir, FileVisitor visitor) throws IOException {
        if (!dir.exists()) {
            return;
        }

        String[] includePatterns = parsePatterns(includes, "**");
        String[] excludePatterns = parsePatterns(excludes, null);
        String[] defaultExcludePatterns =
                useDefaultExcludes ? DirectoryScanner.getDefaultExcludes() : new String[0];
        boolean includeSymlinks = visitor.understandsSymlink();
        Path base = dir.toPath();

        Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() && !includeSymlinks) {
                    return FileVisitResult.CONTINUE;
                }

                String relativePath = base.relativize(file).toString();
                if (matchesPatterns(relativePath, includePatterns, excludePatterns, defaultExcludePatterns)) {
                    visitIfRepresentable(file, relativePath, visitor);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Hands a walked path off to the visitor as a {@link File}, unless the file name cannot be
     * represented losslessly as one under the platform encoding, in which case it is skipped
     * with a warning instead of failing the whole scan.
     */
    private void visitIfRepresentable(Path file, String relativePath, FileVisitor visitor) throws IOException {
        File f = file.toFile();
        if (!f.exists()) {
            LOGGER.log(
                    Level.WARNING,
                    "Skipping ''{0}'' from the cache: its name cannot be represented using this JVM''s file name"
                            + " encoding ({1}). Configure a UTF-8 locale (e.g. LANG/LC_ALL) on this agent to"
                            + " include it.",
                    new Object[] {relativePath, System.getProperty("sun.jnu.encoding")});
            return;
        }
        scanSingle(f, relativePath, visitor);
    }

    /**
     * Checks whether a relative path matches at least one include pattern and none of the
     * exclude patterns, using the same Ant glob semantics as {@link DirectoryScanner}.
     */
    private static boolean matchesPatterns(
            String relativePath, String[] includePatterns, String[] excludePatterns, String[] defaultExcludePatterns) {
        boolean included = false;
        for (String pattern : includePatterns) {
            if (SelectorUtils.matchPath(pattern, relativePath)) {
                included = true;
                break;
            }
        }
        if (!included) {
            return false;
        }

        for (String pattern : excludePatterns) {
            if (SelectorUtils.matchPath(pattern, relativePath)) {
                return false;
            }
        }
        for (String pattern : defaultExcludePatterns) {
            if (SelectorUtils.matchPath(pattern, relativePath)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses a comma-separated pattern string into individual patterns, consistent with how
     * {@link hudson.Util#createFileSet(java.io.File, String, String)} tokenizes includes/excludes.
     * Whitespace around commas is trimmed but not treated as a separator itself.
     *
     * @param patterns the raw pattern string (may be null or empty)
     * @param defaultPattern fallback pattern to use when input is null or empty, or null for empty array
     * @return array of individual patterns
     */
    private static String[] parsePatterns(String patterns, String defaultPattern) {
        if (patterns == null || patterns.isBlank()) {
            return defaultPattern != null ? new String[] {defaultPattern} : new String[0];
        }
        String[] tokens = patterns.split(",");
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.toArray(new String[0]);
    }
}
