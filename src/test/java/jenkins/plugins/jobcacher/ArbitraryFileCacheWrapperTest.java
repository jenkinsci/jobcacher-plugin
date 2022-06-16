package jenkins.plugins.jobcacher;

import hudson.model.FreeStyleProject;
import jenkins.plugins.jobcacher.ArbitraryFileCache.CompressionMethod;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ArbitraryFileCacheWrapperTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testArbitraryFileCacheForm() throws Exception {
        FreeStyleProject project = createProjectWithFullyConfiguredArbitraryFileCache("test");
        String projectConfigXml = project.getConfigFile().asString();

        jenkinsRule.configRoundtrip(project);
        String projectConfigXmlAfterConfigRoundtrip = project.getConfigFile().asString();

        assertThat(projectConfigXmlAfterConfigRoundtrip).isEqualTo(projectConfigXml);
    }

    private FreeStyleProject createProjectWithFullyConfiguredArbitraryFileCache(String name) throws IOException {
        ArbitraryFileCache cache = new ArbitraryFileCache("path", "includes", "excludes");
        cache.setCacheValidityDecidingFile("cacheValidityDecidingFile");
        cache.setCompressionMethod(CompressionMethod.TARGZ);
        cache.setUseDefaultExcludes(false);

        CacheWrapper cacheWrapper = new CacheWrapper(999, Collections.singletonList(cache));
        cacheWrapper.setDefaultBranch("develop");

        FreeStyleProject project = jenkinsRule.createProject(FreeStyleProject.class, name);
        project.setDescription("description");
        project.getBuildWrappersList().add(cacheWrapper);
        project.setAssignedLabel(null);

        return project;
    }

}