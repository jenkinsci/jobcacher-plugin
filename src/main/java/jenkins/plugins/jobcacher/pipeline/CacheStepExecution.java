package jenkins.plugins.jobcacher.pipeline;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.jobcacher.Cache;
import jenkins.plugins.jobcacher.CacheManager;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.IOException;
import java.util.List;

public class CacheStepExecution extends GeneralNonBlockingStepExecution {

    private static final long serialVersionUID = 1L;

    private final Long maxCacheSize;
    private final boolean skipSave;
    private final boolean skipRestore;
    private final List<Cache> caches;
    private final String defaultBranch;

    protected CacheStepExecution(StepContext context, Long maxCacheSize, boolean skipSave, boolean skipRestore, List<Cache> caches, String defaultBranch) {
        super(context);

        this.maxCacheSize = maxCacheSize;
        this.caches = caches;
        this.defaultBranch = defaultBranch;
        this.skipSave = skipSave;
        this.skipRestore = skipRestore;
    }

    @Override
    public boolean start() throws Exception {
        run(this::execute);
        return false;
    }

    private void execute() throws Exception {
        StepContext context = getContext();

        Run<?, ?> run = context.get(Run.class);
        FilePath workspace = context.get(FilePath.class);
        Launcher launcher = context.get(Launcher.class);
        TaskListener listener = context.get(TaskListener.class);
        EnvVars initialEnvironment = context.get(EnvVars.class);

        List<Cache.Saver> cacheSavers = CacheManager.cache(GlobalItemStorage.get().getStorage(), run, workspace, launcher, listener, initialEnvironment, caches, defaultBranch, skipRestore);

        context.newBodyInvoker()
                .withContext(context)
                .withCallback(new NonBlockingExecutionCallback(maxCacheSize, skipSave, caches, cacheSavers))
                .start();
    }

    private class NonBlockingExecutionCallback extends BodyExecutionCallback {

        private static final long serialVersionUID = 1L;

        private final Long maxCacheSize;
        private final boolean skipSave;
        private final List<Cache> caches;
        private final List<Cache.Saver> cacheSavers;

        public NonBlockingExecutionCallback(Long maxCacheSize, boolean skipSave, List<Cache> caches, List<Cache.Saver> cacheSavers) {
            this.maxCacheSize = maxCacheSize;
            this.skipSave = skipSave;
            this.caches = caches;
            this.cacheSavers = cacheSavers;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            run(() -> {
                try {
                    complete(context);

                    context.onSuccess(result);
                } catch (Throwable t) {
                    context.onFailure(t);
                }
            });
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            context.onFailure(t);
        }

        private void complete(StepContext context) throws IOException, InterruptedException {
            TaskListener listener = context.get(TaskListener.class);
            if (skipSave) {
                listener.getLogger().println("Skipping save due to skipSave being set to true.");
                return;
            }
            Run<?, ?> run = context.get(Run.class);
            FilePath workspace = context.get(FilePath.class);
            Launcher launcher = context.get(Launcher.class);

            CacheManager.save(GlobalItemStorage.get().getStorage(), run, workspace, launcher, listener, maxCacheSize, caches, cacheSavers);
        }
    }

    /**
     * This implementation has been replaced with {@link NonBlockingExecutionCallback}, but is kept to not break running builds during upgrade.
     * Remove it if you think there have been enough releases in the meantime.
     */
    private static class ExecutionCallback extends BodyExecutionCallback {

        private static final long serialVersionUID = 1L;

        private final Long maxCacheSize;
        private final boolean skipSave;
        private final List<Cache> caches;
        private final List<Cache.Saver> cacheSavers;

        public ExecutionCallback(Long maxCacheSize, boolean skipSave, List<Cache> caches, List<Cache.Saver> cacheSavers) {
            this.maxCacheSize = maxCacheSize;
            this.skipSave = skipSave;
            this.caches = caches;
            this.cacheSavers = cacheSavers;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            try {
                complete(context);

                context.onSuccess(result);
            } catch (Throwable t) {
                context.onFailure(t);
            }
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            context.onFailure(t);
        }

        private void complete(StepContext context) throws IOException, InterruptedException {
            TaskListener listener = context.get(TaskListener.class);
            if (skipSave) {
                listener.getLogger().println("Skipping save due to skipSave being set to true.");
                return;
            }
            Run<?, ?> run = context.get(Run.class);
            FilePath workspace = context.get(FilePath.class);
            Launcher launcher = context.get(Launcher.class);

            CacheManager.save(GlobalItemStorage.get().getStorage(), run, workspace, launcher, listener, maxCacheSize, caches, cacheSavers);
        }
    }

}
