package io.jenkins.plugins.pipeline.cache;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Optional;
import java.util.stream.Stream;

import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.FilePath;
import hudson.model.TaskListener;
import io.jenkins.plugins.pipeline.cache.agent.BackupCallable;
import io.jenkins.plugins.pipeline.cache.agent.RestoreCallable;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.ItemStorage;

/**
 * Executes pipeline step 'cache'.
 */
public class CacheStepExecution extends GeneralNonBlockingStepExecution {

    private static final long serialVersionUID = 1L;

    private final transient PrintStream logger;
    private final String path;
    private final String key;
    private final String[] restoreKeys;
    private final String filter;
    private final ItemStorage<?> config;

    public CacheStepExecution(
            StepContext context,
            String path,
            String key,
            String[] restoreKeys,
            String filter) throws IOException, InterruptedException {
        super(context);
        this.path = path;
        this.key = key;
        this.restoreKeys = restoreKeys;
        this.filter = filter;
        this.logger = context.get(TaskListener.class).getLogger();
        this.config = GlobalItemStorage.get().getStorage();
    }

    @Override
    public boolean start() throws Exception {
        FilePath workspace = getContext().get(FilePath.class);
        FilePath path = workspace.child(this.path);
        String[] restoreKeys = Stream.concat(
                        Stream.of(this.key),
                        Stream.of(Optional.ofNullable(this.restoreKeys).orElse(new String[0]))
                ).toArray(String[]::new);

        // restore existing cache
        path.act(new RestoreCallable(config, restoreKeys)).printInfos(logger);

        // execute inner-step and save cache afterwards
        getContext().newBodyInvoker().withCallback(new BodyExecutionCallback() {
            @Override
            public void onSuccess(StepContext context, Object result) {
                try {
                    path.act(new BackupCallable(config, key, filter)).printInfos(logger);
                } catch (Exception x) {
                    context.onFailure(x);
                    return;
                }
                context.onSuccess(result);
            }

            @Override
            public void onFailure(StepContext context, Throwable t) {
                logger.println("Cache not saved (inner-step execution failed)");
                context.onFailure(t);
            }
        }).start();

        return false;
    }

}
