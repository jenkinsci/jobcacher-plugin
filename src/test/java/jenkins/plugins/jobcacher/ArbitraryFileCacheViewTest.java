package jenkins.plugins.jobcacher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.model.Result;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.ObjectPath;
import org.htmlunit.Page;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.WithTimeout;

/**
 * Tests for {@link ArbitraryFileCache#doDynamic} — the handler that serves the per-job
 * "Caches" sidebar view. The lookup must locate the on-disk archive regardless of the
 * currently-configured {@code compressionMethod}, since the configured method can drift
 * away from the format that was actually written by the most recent successful save.
 */
@WithJenkins
class ArbitraryFileCacheViewTest {

    private static JenkinsRule jenkins;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        jenkins = rule;
    }

    @Test
    @WithTimeout(120)
    void servesTarGzCache() throws Exception {
        WorkflowJob project = createProjectWithCache("TARGZ");
        jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));

        assertCacheViewStatus(project, 200);
    }

    @Test
    @WithTimeout(120)
    void servesTarZstdCache() throws Exception {
        WorkflowJob project = createProjectWithCache("TAR_ZSTD");
        jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));

        assertCacheViewStatus(project, 200);
    }

    @Test
    @WithTimeout(120)
    void returnsNoCacheViewWhenCacheIsAbsent() throws Exception {
        WorkflowJob project = createProjectWithCache("TARGZ");
        jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));

        // Wipe the on-disk archive while keeping the CacheProjectAction attached to the job.
        ObjectPath cachesRoot =
                CacheManager.getCachePath(GlobalItemStorage.get().getStorage(), project);
        cachesRoot.deleteRecursive();

        assertCacheViewStatus(project, 404);
    }

    @Test
    @WithTimeout(120)
    void servesCacheAfterCompressionMethodChange() throws Exception {
        // Build with TARGZ — archive written as <hash>.tgz.
        WorkflowJob project = createProjectWithCache("TARGZ");
        WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));

        // Simulate the user editing the pipeline to switch to TAR_ZSTD without running a new build
        // yet: the on-disk .tgz remains, but the live ArbitraryFileCache instance now claims TAR_ZSTD.
        CacheProjectAction projectAction = (CacheProjectAction) run.getAction(CacheBuildLastAction.class)
                .getProjectActions()
                .iterator()
                .next();
        ArbitraryFileCache cache = (ArbitraryFileCache) projectAction.getCaches().get(0);
        cache.setCompressionMethod(ArbitraryFileCache.CompressionMethod.TAR_ZSTD);

        // The view must still find the .tgz on disk and serve it; otherwise users would see
        // a 404 between the config change and the next successful save.
        assertCacheViewStatus(project, 200);
    }

    private WorkflowJob createProjectWithCache(String compressionMethod) throws Exception {
        WorkflowJob project = jenkins.createProject(WorkflowJob.class);
        String pipeline = "node {\n"
                + "  cache(maxCacheSize: 100, caches: [arbitraryFileCache(path: 'test-path', compressionMethod: '"
                + compressionMethod + "')]) {\n"
                + "    writeFile text: 'cached-content', file: 'test-path/data.txt'\n"
                + "  }\n"
                + "}";
        project.setDefinition(new CpsFlowDefinition(pipeline, true));
        return project;
    }

    private void assertCacheViewStatus(WorkflowJob project, int expectedStatusCode) throws Exception {
        try (JenkinsRule.WebClient wc = jenkins.createWebClient()) {
            wc.setThrowExceptionOnFailingStatusCode(false);
            Page page = wc.goTo(project.getUrl() + "cache/caches/0/", null);
            assertThat(page.getWebResponse().getStatusCode(), is(expectedStatusCode));
        }
    }
}
