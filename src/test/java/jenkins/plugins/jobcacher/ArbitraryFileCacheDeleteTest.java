package jenkins.plugins.jobcacher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Result;
import jenkins.model.Jenkins;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.ObjectPath;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.WithTimeout;

/**
 * Tests for the manual cache deletion offered by the per-job "Caches" view - {@link Cache#doDelete} and
 * {@link CacheProjectAction#doDeleteAll}.
 */
@WithJenkins
class ArbitraryFileCacheDeleteTest {

    private static final String CACHE_PATH = "test-path";
    private static final String ARCHIVE_EXTENSION = ".tgz";
    private static final String VALIDITY_HASH_EXTENSION = ".hash";

    private static JenkinsRule jenkins;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        jenkins = rule;
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        jenkins.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ, Item.BUILD)
                .everywhere()
                .to("builder")
                .grant(Jenkins.READ, Item.READ)
                .everywhere()
                .to("reader"));
    }

    @Test
    @WithTimeout(120)
    void deletesCacheOfSelectedEntryOnly() throws Exception {
        WorkflowJob project = createProjectWithCaches(CACHE_PATH, "other-path");
        jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));

        assertThat(cacheArchive(project, CACHE_PATH).exists(), is(true));
        assertThat(cacheArchive(project, "other-path").exists(), is(true));

        assertThat(post("builder", project.getUrl() + "cache/caches/0/delete"), is(200));

        assertThat(cacheArchive(project, CACHE_PATH).exists(), is(false));
        assertThat(cacheArchive(project, "other-path").exists(), is(true));
    }

    @Test
    @WithTimeout(120)
    void deletesCacheValidityDecidingFileHashAlongWithCache() throws Exception {
        WorkflowJob project = jenkins.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile text: 'lock', file: 'deciding.txt'
                  cache(maxCacheSize: 100, caches: [arbitraryFileCache(path: 'test-path', cacheValidityDecidingFile: 'deciding.txt')]) {
                    writeFile text: 'cached-content', file: 'test-path/data.txt'
                  }
                }
                """, true));
        jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));

        assertThat(cacheArchive(project, CACHE_PATH).exists(), is(true));
        assertThat(cacheValidityHash(project, CACHE_PATH).exists(), is(true));

        assertThat(post("builder", project.getUrl() + "cache/caches/0/delete"), is(200));

        assertThat(cacheArchive(project, CACHE_PATH).exists(), is(false));
        assertThat(cacheValidityHash(project, CACHE_PATH).exists(), is(false));
    }

    @Test
    @WithTimeout(120)
    void deletesCacheWrittenByAnotherCompressionMethod() throws Exception {
        // Build with TARGZ - the archive is written as <hash>.tgz.
        WorkflowJob project = createProjectWithCaches(CACHE_PATH);
        WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));

        // Simulate the user switching the compression method without running a new build: the live cache
        // instance claims TAR_ZSTD while the on-disk archive is still a .tgz.
        cacheOf(run).setCompressionMethod(ArbitraryFileCache.CompressionMethod.TAR_ZSTD);

        assertThat(post("builder", project.getUrl() + "cache/caches/0/delete"), is(200));

        assertThat(cacheArchive(project, CACHE_PATH).exists(), is(false));
    }

    @Test
    @WithTimeout(120)
    void deleteAllRemovesCachesIncludingOnesNotListedAnymore() throws Exception {
        WorkflowJob project = createProjectWithCaches(CACHE_PATH);
        jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));

        // A cache left behind by a configuration that has been removed in the meantime is not listed in the
        // view anymore, so it can only be removed by deleting all caches of the job.
        ObjectPath orphanedCache = createOrphanedCache(project);
        assertThat(orphanedCache.exists(), is(true));

        assertThat(post("builder", project.getUrl() + "cache/deleteAll"), is(200));

        assertThat(cacheArchive(project, CACHE_PATH).exists(), is(false));
        assertThat(orphanedCache.exists(), is(false));
        assertThat(cachesRoot(project).exists(), is(false));
    }

    @Test
    @WithTimeout(120)
    void keepsCacheOnGetRequest() throws Exception {
        WorkflowJob project = createProjectWithCaches(CACHE_PATH);
        jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));

        assertThat(get("builder", project.getUrl() + "cache/caches/0/delete"), is(405));
        assertThat(get("builder", project.getUrl() + "cache/deleteAll"), is(405));

        assertThat(cacheArchive(project, CACHE_PATH).exists(), is(true));
    }

    @Test
    @WithTimeout(120)
    void keepsCacheForUserWithoutBuildPermission() throws Exception {
        WorkflowJob project = createProjectWithCaches(CACHE_PATH);
        jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));

        assertThat(post("reader", project.getUrl() + "cache/caches/0/delete"), is(403));
        assertThat(post("reader", project.getUrl() + "cache/deleteAll"), is(403));

        assertThat(cacheArchive(project, CACHE_PATH).exists(), is(true));
    }

    @Test
    @WithTimeout(120)
    void showsDeletionControlsOnlyToUsersWithBuildPermission() throws Exception {
        WorkflowJob project = createProjectWithCaches(CACHE_PATH);
        jenkins.assertBuildStatus(Result.SUCCESS, project.scheduleBuild2(0));

        assertThat(cacheViewContains("builder", project, "Delete all caches"), is(true));
        assertThat(cacheViewContains("reader", project, "Delete all caches"), is(false));
    }

    private WorkflowJob createProjectWithCaches(String... paths) throws Exception {
        StringBuilder cacheDefinitions = new StringBuilder();
        StringBuilder cacheContents = new StringBuilder();
        for (String path : paths) {
            if (!cacheDefinitions.isEmpty()) {
                cacheDefinitions.append(", ");
            }
            cacheDefinitions.append("arbitraryFileCache(path: '").append(path).append("')");
            cacheContents
                    .append("    writeFile text: 'cached-content', file: '")
                    .append(path)
                    .append("/data.txt'\n");
        }

        WorkflowJob project = jenkins.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition(
                "node {\n" + "  cache(maxCacheSize: 100, caches: ["
                        + cacheDefinitions + "]) {\n"
                        + cacheContents + "  }\n"
                        + "}",
                true));

        return project;
    }

    private ArbitraryFileCache cacheOf(WorkflowRun run) {
        CacheProjectAction projectAction = (CacheProjectAction) run.getAction(CacheBuildLastAction.class)
                .getProjectActions()
                .iterator()
                .next();

        return (ArbitraryFileCache) projectAction.getCaches().get(0);
    }

    private ObjectPath cachesRoot(WorkflowJob project) {
        return CacheManager.getCachePath(GlobalItemStorage.get().getStorage(), project);
    }

    private ObjectPath cacheArchive(WorkflowJob project, String path) throws Exception {
        return cachesRoot(project).child(Cache.deriveCachePath(path) + ARCHIVE_EXTENSION);
    }

    private ObjectPath cacheValidityHash(WorkflowJob project, String path) throws Exception {
        return cachesRoot(project).child(Cache.deriveCachePath(path) + VALIDITY_HASH_EXTENSION);
    }

    private ObjectPath createOrphanedCache(WorkflowJob project) throws Exception {
        FilePath source = new FilePath(jenkins.jenkins.getRootDir()).child("orphaned-cache-source");
        source.write("orphaned-content", "UTF-8");

        ObjectPath orphanedCache = cachesRoot(project).child("orphaned-cache" + ARCHIVE_EXTENSION);
        orphanedCache.copyFrom(source);

        return orphanedCache;
    }

    private int post(String user, String relativeUrl) throws Exception {
        try (JenkinsRule.WebClient wc = createWebClient(user)) {
            WebRequest request = new WebRequest(wc.createCrumbedUrl(relativeUrl), HttpMethod.POST);

            return wc.getPage(request).getWebResponse().getStatusCode();
        }
    }

    private int get(String user, String relativeUrl) throws Exception {
        try (JenkinsRule.WebClient wc = createWebClient(user)) {
            return wc.goTo(relativeUrl, null).getWebResponse().getStatusCode();
        }
    }

    private boolean cacheViewContains(String user, WorkflowJob project, String text) throws Exception {
        try (JenkinsRule.WebClient wc = createWebClient(user)) {
            Page page = wc.goTo(project.getUrl() + "cache/");

            return page.getWebResponse().getContentAsString().contains(text);
        }
    }

    private JenkinsRule.WebClient createWebClient(String user) throws Exception {
        JenkinsRule.WebClient wc = jenkins.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);

        return wc.login(user);
    }
}
