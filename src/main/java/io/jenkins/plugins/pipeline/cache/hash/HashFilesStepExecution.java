package io.jenkins.plugins.pipeline.cache.hash;

import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.IteratorUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import hudson.FilePath;

/**
 * Collects selected files, creates one hash for all files and returns the hash as result value. If there are no files selected at all
 * then d41d8cd98f00b204e9800998ecf8427e is returned (md5 hash of an empty string).
 */
public class HashFilesStepExecution extends SynchronousNonBlockingStepExecution<String> {
    private final String pattern;

    public HashFilesStepExecution(StepContext context, String pattern) {
        super(context);
        this.pattern = pattern;
    }

    @Override
    protected String run() throws Exception {
        FilePath workspace = getContext().get(FilePath.class);
        Iterator<InputStream> files = Arrays.stream(workspace.list(pattern))
                .sorted()
                .map(filePath -> {
                    try {
                        return filePath.read();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .iterator();

        try (SequenceInputStream sequenceInputStream = new SequenceInputStream(IteratorUtils.asEnumeration(files))) {
            return DigestUtils.md5Hex(sequenceInputStream);
        }
    }
}
