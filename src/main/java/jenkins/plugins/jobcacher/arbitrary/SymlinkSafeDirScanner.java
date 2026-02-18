package jenkins.plugins.jobcacher.arbitrary;

import hudson.Util;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.selectors.SelectorUtils;

/**
 * A directory scanner that archives the directory as-is, preserving symlinks as native entries
 * when the archiver supports them (e.g., TAR).
 *
 * <p>This replaces the blanket symlink-skipping behavior of {@code LinkOption.NOFOLLOW_LINKS}
 * passed to {@code FilePath.archive()}, which strips all symlinks including ones needed by
 * tools like npm/yarn (e.g., {@code node_modules/.bin} symlinks).
 *
 * <p>The scanning strategy:
 * <ol>
 *   <li>Use Ant's {@link DirectoryScanner} with {@code followSymlinks=false} to discover
 *       all real (non-symlink) files matching the includes/excludes pattern.</li>
 *   <li>If the archiver supports symlinks, walk the directory tree to find symlinks that
 *       match the same includes/excludes patterns, and forward them via {@code scanSingle()}
 *       so the archiver stores them as native entries (no target content is read).</li>
 * </ol>
 */
class SymlinkSafeDirScanner extends DirScanner {

    private static final long serialVersionUID = 1L;

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

        scanRealFiles(dir, visitor);

        if (visitor.understandsSymlink()) {
            String[] includePatterns = parsePatterns(includes, "**");
            String[] excludePatterns = parsePatterns(excludes, null);
            String[] defaultExcludePatterns =
                    useDefaultExcludes ? DirectoryScanner.getDefaultExcludes() : new String[0];
            scanSymlinks(dir, dir, visitor, includePatterns, excludePatterns, defaultExcludePatterns);
        }
    }

    /**
     * Scans real (non-symlink) files using Ant's {@link DirectoryScanner} with
     * {@code followSymlinks=false}, so symlinks are not discovered or followed.
     */
    private void scanRealFiles(File dir, FileVisitor visitor) throws IOException {
        FileSet fs = Util.createFileSet(dir, includes, excludes);
        fs.setFollowSymlinks(false);
        fs.setDefaultexcludes(useDefaultExcludes);

        DirectoryScanner ds = fs.getDirectoryScanner(new Project());
        for (String relativePath : ds.getIncludedFiles()) {
            scanSingle(new File(dir, relativePath), relativePath, visitor);
        }
    }

    /**
     * Recursively walks real directories to find symlinks matching the includes/excludes
     * patterns, and forwards them to the visitor via {@code scanSingle()}.
     * Does not recurse into symlinked directories to avoid circular references.
     */
    private void scanSymlinks(
            File current,
            File baseDir,
            FileVisitor visitor,
            String[] includePatterns,
            String[] excludePatterns,
            String[] defaultExcludePatterns)
            throws IOException {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (Files.isSymbolicLink(child.toPath())) {
                String relativePath = baseDir.toPath().relativize(child.toPath()).toString();
                if (matchesPatterns(relativePath, includePatterns, excludePatterns, defaultExcludePatterns)) {
                    scanSingle(child, relativePath, visitor);
                }
            } else if (child.isDirectory()) {
                scanSymlinks(child, baseDir, visitor, includePatterns, excludePatterns, defaultExcludePatterns);
            }
        }
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
