package jenkins.plugins.jobcacher.arbitrary;

import hudson.FilePath;
import hudson.Util;
import hudson.slaves.WorkspaceList;

import java.io.IOException;
import java.util.UUID;

public final class WorkspaceHelper {

    private WorkspaceHelper() {
    }

    public static TempFile createTempFile(FilePath workspace, String fileExtension) throws IOException, InterruptedException {
        FilePath tempDir = getTempDir(workspace);
        FilePath tempFile = tempDir.createTempFile(generateTempFileName(), fileExtension);

        return new TempFile(tempFile);
    }

    private static FilePath getTempDir(FilePath workspace) throws IOException, InterruptedException {
        FilePath filePath = WorkspaceList.tempDir(workspace);
        if (filePath == null) {
            throw new IllegalStateException("could not resolve temporary directory");
        }
        filePath.mkdirs();

        return filePath;
    }

    private static String generateTempFileName() {
        return Util.getDigestOf(UUID.randomUUID().toString());
    }

    public static class TempFile implements AutoCloseable {

        private final FilePath tempFile;

        private TempFile(FilePath tempFile) {
            this.tempFile = tempFile;
        }

        public FilePath get() {
            return tempFile;
        }

        @Override
        public void close() throws IOException, InterruptedException {
            tempFile.delete();
        }
    }
}
