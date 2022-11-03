package jenkins.plugins.jobcacher.arbitrary;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import hudson.util.io.ArchiverFactory;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.*;
import java.nio.file.Files;

public class TarArbitraryFileCacheStrategy extends AbstractCompressingArbitraryFileCacheStrategy {

    private final String compressorName;
    private final String archiveExtension;

    public TarArbitraryFileCacheStrategy(String compressorName, String archiveExtension) {
        this.compressorName = compressorName;
        this.archiveExtension = archiveExtension;
    }

    @Override
    protected String getArchiveExtension() {
        return archiveExtension;
    }

    @Override
    protected void uncompress(FilePath source, FilePath target) throws IOException, InterruptedException {
        source.act(new ExtractTarCallable(target, compressorName));
    }

    @Override
    protected void compress(FilePath source, String includes, String excludes, boolean useDefaultExcludes, FilePath target) throws IOException, InterruptedException {
        target.act(new CreateTarCallable(source, includes, excludes, useDefaultExcludes, compressorName));
    }

    private static class ExtractTarCallable extends MasterToSlaveFileCallable<Void> {

        private final FilePath target;
        private final String compressorName;

        public ExtractTarCallable(FilePath target, String compressorName) {
            this.target = target;
            this.compressorName = compressorName;
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
            inputStream = new CompressorStreamFactory().createCompressorInputStream(compressorName, inputStream);

            return new BufferedInputStream(inputStream);
        }
    }

    private static class CreateTarCallable extends MasterToSlaveFileCallable<Void> {

        private final FilePath source;
        private final String includes;
        private final String excludes;
        private final boolean useDefaultExcludes;
        private final String compressorName;

        public CreateTarCallable(FilePath source, String includes, String excludes, boolean useDefaultExcludes, String compressorName) {
            this.source = source;
            this.includes = includes;
            this.excludes = excludes;
            this.useDefaultExcludes = useDefaultExcludes;
            this.compressorName = compressorName;
        }

        @Override
        public Void invoke(File targetFile, VirtualChannel channel) throws IOException, InterruptedException {
            try (OutputStream outputStream = createOutputStream(targetFile)) {
                source.archive(ArchiverFactory.TAR, outputStream, new DirScanner.Glob(includes, excludes, useDefaultExcludes));
            } catch (CompressorException e) {
                throw new IOException(e);
            }

            return null;
        }

        private OutputStream createOutputStream(File targetFile) throws IOException, CompressorException {
            OutputStream outputStream = Files.newOutputStream(targetFile.toPath());
            outputStream = new BufferedOutputStream(outputStream);
            outputStream = new CompressorStreamFactory().createCompressorOutputStream(compressorName, outputStream);

            return outputStream;
        }
    }

}
