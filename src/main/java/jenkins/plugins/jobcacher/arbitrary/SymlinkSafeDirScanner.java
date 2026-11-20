package jenkins.plugins.jobcacher.arbitrary;

import hudson.model.TaskListener;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
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
 * <p>The directory tree is walked with NIO without following symlinks. Regular files matching the
 * includes/excludes patterns (same Ant glob semantics as {@link DirectoryScanner}) are forwarded to
 * the visitor. If the archiver supports symlinks, matching symlinks are forwarded as well via
 * {@code scanSingle()} so the archiver stores them as native entries (no target content is read).
 *
 * <p>Files whose name cannot be represented in the JVM's file name encoding ({@code sun.jnu.encoding}),
 * e.g. {@code nvue - 副本.js} on an agent running without a UTF-8 locale, are skipped and reported to
 * the build log. The archivers only accept {@link File}s, and such a {@link File} cannot be converted back
 * to the {@link Path} of the file on disk, so archiving it would fail the whole cache creation
 * (<a href="https://github.com/jenkinsci/jobcacher-plugin/issues/246">#246</a>).
 */
class SymlinkSafeDirScanner extends DirScanner {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int MAX_REPORTED_SKIPPED_FILES = 10;

    private final String includes;
    private final String excludes;
    private final boolean useDefaultExcludes;
    private final TaskListener listener;

    SymlinkSafeDirScanner(String includes, String excludes, boolean useDefaultExcludes, TaskListener listener) {
        this.includes = includes;
        this.excludes = excludes;
        this.useDefaultExcludes = useDefaultExcludes;
        this.listener = listener;
    }

    @Override
    public void scan(File dir, FileVisitor visitor) throws IOException {
        if (!dir.exists()) {
            return;
        }

        String[] includePatterns = parsePatterns(includes, "**");
        List<String> excludePatterns = new ArrayList<>(List.of(parsePatterns(excludes, null)));
        if (useDefaultExcludes) {
            for (String defaultExclude : DirectoryScanner.getDefaultExcludes()) {
                excludePatterns.add(normalizePattern(defaultExclude));
            }
        }

        Path baseDir = dir.toPath();
        List<String> skippedFiles = new ArrayList<>();
        Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                if (directory.equals(baseDir)) {
                    return FileVisitResult.CONTINUE;
                }
                String relativePath = baseDir.relativize(directory).toString();
                if (!couldHoldIncluded(relativePath, includePatterns)
                        || contentsExcluded(relativePath, excludePatterns)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!isAccessibleByName(directory)) {
                    skippedFiles.add(relativePath + File.separator);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                boolean archivable = attrs.isRegularFile() || (attrs.isSymbolicLink() && visitor.understandsSymlink());
                if (!archivable) {
                    return FileVisitResult.CONTINUE;
                }
                String relativePath = baseDir.relativize(file).toString();
                if (!matchesPatterns(relativePath, includePatterns, excludePatterns)) {
                    return FileVisitResult.CONTINUE;
                }
                if (!isAccessibleByName(file)) {
                    skippedFiles.add(relativePath);
                    return FileVisitResult.CONTINUE;
                }
                scanSingle(file.toFile(), relativePath, visitor);
                return FileVisitResult.CONTINUE;
            }
        });

        reportSkippedFiles(skippedFiles);
    }

    /**
     * Whether the file can be reached again through its name as a {@link String}, which is what
     * {@link File} based APIs (including the archivers) do. This is not the case when the JVM's file
     * name encoding cannot represent the name, as the name is then decoded lossily.
     */
    private static boolean isAccessibleByName(Path path) {
        try {
            return path.toFile().toPath().equals(path);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private void reportSkippedFiles(List<String> skippedFiles) {
        if (skippedFiles.isEmpty()) {
            return;
        }
        listener.getLogger()
                .println("WARNING: Skipped " + skippedFiles.size() + " file(s) whose name is not valid in the file name"
                        + " encoding of the agent JVM (sun.jnu.encoding=" + System.getProperty("sun.jnu.encoding")
                        + "). If the agent does not run with a UTF-8 locale, start it with one (e.g. LANG=C.UTF-8)"
                        + " to cache them:");
        skippedFiles.stream()
                .limit(MAX_REPORTED_SKIPPED_FILES)
                .forEach(skippedFile -> listener.getLogger().println("  " + skippedFile));
        if (skippedFiles.size() > MAX_REPORTED_SKIPPED_FILES) {
            listener.getLogger().println("  ... and " + (skippedFiles.size() - MAX_REPORTED_SKIPPED_FILES) + " more");
        }
    }

    /**
     * Checks whether a relative path matches at least one include pattern and none of the
     * exclude patterns, using the same Ant glob semantics as {@link DirectoryScanner}.
     */
    private static boolean matchesPatterns(
            String relativePath, String[] includePatterns, List<String> excludePatterns) {
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
        return true;
    }

    /**
     * Whether files below the given directory could match an include pattern, like
     * {@code DirectoryScanner#couldHoldIncluded}. Used to avoid walking directories needlessly.
     */
    private static boolean couldHoldIncluded(String relativeDir, String[] includePatterns) {
        for (String pattern : includePatterns) {
            if (SelectorUtils.matchPatternStart(pattern, relativeDir)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether all files below the given directory are excluded (e.g. by {@code **}{@code /node_modules/**}),
     * like {@code DirectoryScanner#contentsExcluded}. Used to avoid walking directories needlessly.
     */
    private static boolean contentsExcluded(String relativeDir, List<String> excludePatterns) {
        String deepTreeSuffix = File.separator + SelectorUtils.DEEP_TREE_MATCH;
        for (String pattern : excludePatterns) {
            if (pattern.endsWith(deepTreeSuffix)
                    && SelectorUtils.matchPath(
                            pattern.substring(0, pattern.length() - deepTreeSuffix.length()), relativeDir)) {
                return true;
            }
        }
        return false;
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
                result.add(normalizePattern(trimmed));
            }
        }
        return result.toArray(new String[0]);
    }

    /**
     * Normalizes a pattern like {@code DirectoryScanner} does: uses the platform separator and
     * treats a trailing separator as {@code **}.
     */
    private static String normalizePattern(String pattern) {
        String normalized = pattern.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        if (normalized.endsWith(File.separator)) {
            normalized += SelectorUtils.DEEP_TREE_MATCH;
        }
        return normalized;
    }
}
