package jenkins.plugins.jobcacher.arbitrary;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.io.ArchiverFactory;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import jenkins.agents.ControllerToAgentFileCallable;

public class ZipArbitraryFileCacheStrategy extends AbstractCompressingArbitraryFileCacheStrategy {

    @Override
    protected String getArchiveExtension() {
        return ".zip";
    }

    @Override
    protected void uncompress(FilePath source, FilePath target) throws IOException, InterruptedException {
        source.unzip(target);
    }

    @Override
    protected void compress(
            FilePath source,
            String includes,
            String excludes,
            boolean useDefaultExcludes,
            FilePath target,
            TaskListener listener)
            throws IOException, InterruptedException {
        target.act(new CreateZipCallable(source, includes, excludes, useDefaultExcludes, listener));
    }

    private record CreateZipCallable(
            FilePath source, String includes, String excludes, boolean useDefaultExcludes, TaskListener listener)
            implements ControllerToAgentFileCallable<Void> {

        @Override
        public Void invoke(File targetFile, VirtualChannel channel) throws IOException, InterruptedException {
            try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(targetFile.toPath()))) {
                source.archive(
                        ArchiverFactory.ZIP,
                        outputStream,
                        new SymlinkSafeDirScanner(includes, excludes, useDefaultExcludes, listener));
            }

            return null;
        }
    }
}
