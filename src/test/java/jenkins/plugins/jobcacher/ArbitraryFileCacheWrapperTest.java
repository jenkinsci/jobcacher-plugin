package jenkins.plugins.jobcacher;

import hudson.model.FreeStyleProject;
import jenkins.plugins.jobcacher.ArbitraryFileCache.CompressionMethod;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class ArbitraryFileCacheWrapperTest {

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testArbitraryFileCacheForm() throws Exception {
        FreeStyleProject project = createProjectWithFullyConfiguredArbitraryFileCache("test");
        String projectConfigXml = project.getConfigFile().asString();

        jenkins.configRoundtrip(project);
        String projectConfigXmlAfterConfigRoundtrip = project.getConfigFile().asString();

        assertThat(projectConfigXmlAfterConfigRoundtrip, is(projectConfigXml));
    }

    private FreeStyleProject createProjectWithFullyConfiguredArbitraryFileCache(String name) throws IOException {
        ArbitraryFileCache cache = new ArbitraryFileCache("path", "includes", "excludes");
        cache.setCacheValidityDecidingFile("cacheValidityDecidingFile");
        cache.setCompressionMethod(CompressionMethod.TARGZ);
        cache.setUseDefaultExcludes(false);
        cache.setCacheName("cacheName");

        CacheWrapper cacheWrapper = new CacheWrapper(Collections.singletonList(cache));
        cacheWrapper.setMaxCacheSize(999L);
        cacheWrapper.setDefaultBranch("develop");
        cacheWrapper.setSkipSave(false);

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, name);
        project.setDescription("description");
        project.getBuildWrappersList().add(cacheWrapper);
        project.setAssignedLabel(null);

        return project;
    }
}
