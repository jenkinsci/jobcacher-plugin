package jenkins.plugins.jobcacher.arbitrary;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import hudson.util.io.ArchiverFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.compress.compressors.CompressorException;

public class TarArbitraryFileCacheStrategy extends AbstractCompressingArbitraryFileCacheStrategy {

    private final CompressingOutputStreamFactory compressingOutputStreamFactory;
    private final CompressingInputStreamFactory compressingInputStreamFactory;
    private final String archiveExtension;

    public TarArbitraryFileCacheStrategy(
            CompressingOutputStreamFactory compressingOutputStreamFactory,
            CompressingInputStreamFactory compressingInputStreamFactory,
            String archiveExtension) {

        this.compressingOutputStreamFactory = compressingOutputStreamFactory;
        this.compressingInputStreamFactory = compressingInputStreamFactory;
        this.archiveExtension = archiveExtension;
    }

    @Override
    protected String getArchiveExtension() {
        return archiveExtension;
    }

    @Override
    protected void uncompress(FilePath source, FilePath target) throws IOException, InterruptedException {
        source.act(new ExtractTarCallable(target, compressingInputStreamFactory));
    }

    @Override
    protected void compress(
            FilePath source, String includes, String excludes, boolean useDefaultExcludes, FilePath target)
            throws IOException, InterruptedException {
        target.act(
                new CreateTarCallable(source, includes, excludes, useDefaultExcludes, compressingOutputStreamFactory));
    }

    private static class ExtractTarCallable extends MasterToSlaveFileCallable<Void> {

        private final FilePath target;
        private final CompressingInputStreamFactory compressingInputStreamFactory;

        public ExtractTarCallable(FilePath target, CompressingInputStreamFactory compressingInputStreamFactory) {
            this.target = target;
            this.compressingInputStreamFactory = compressingInputStreamFactory;
        }

        @Override
        public Void invoke(File sourceFile, VirtualChannel channel) throws IOException, InterruptedException {
            try (InputStream inputStream = createInputStream(sourceFile)) {
                target.untarFrom(inputStream, FilePath.TarCompression.NONE);
            } catch (CompressorException e) {
                throw new IOException(e);
            }

            return null;
        }

        private InputStream createInputStream(File sourceFile) throws IOException, CompressorException {
            InputStream inputStream = Files.newInputStream(sourceFile.toPath());
            inputStream = new BufferedInputStream(inputStream);
            inputStream = compressingInputStreamFactory.createCompressingInputStream(inputStream);

            return new BufferedInputStream(inputStream);
        }
    }

    private static class CreateTarCallable extends MasterToSlaveFileCallable<Void> {

        private final FilePath source;
        private final String includes;
        private final String excludes;
        private final boolean useDefaultExcludes;
        private final CompressingOutputStreamFactory compressingOutputStreamFactory;

        public CreateTarCallable(
                FilePath source,
                String includes,
                String excludes,
                boolean useDefaultExcludes,
                CompressingOutputStreamFactory compressingOutputStreamFactory) {
            this.source = source;
            this.includes = includes;
            this.excludes = excludes;
            this.useDefaultExcludes = useDefaultExcludes;
            this.compressingOutputStreamFactory = compressingOutputStreamFactory;
        }

        @Override
        public Void invoke(File targetFile, VirtualChannel channel) throws IOException, InterruptedException {
            try (OutputStream outputStream = createOutputStream(targetFile)) {
                source.archive(
                        ArchiverFactory.TAR,
                        outputStream,
                        new DirScanner.Glob(includes, excludes, useDefaultExcludes),
                        source.getRemote(),
                        LinkOption.NOFOLLOW_LINKS);
            } catch (CompressorException e) {
                throw new IOException(e);
            }

            return null;
        }

        private OutputStream createOutputStream(File targetFile) throws IOException, CompressorException {
            OutputStream outputStream = Files.newOutputStream(targetFile.toPath());
            outputStream = new BufferedOutputStream(outputStream);
            outputStream = compressingOutputStreamFactory.createCompressingOutputStream(outputStream);

            return outputStream;
        }
    }

    public interface CompressingOutputStreamFactory extends Serializable {

        OutputStream createCompressingOutputStream(OutputStream outputStream) throws IOException;
    }

    public interface CompressingInputStreamFactory extends Serializable {

        InputStream createCompressingInputStream(InputStream inputStream) throws IOException;
    }
}
