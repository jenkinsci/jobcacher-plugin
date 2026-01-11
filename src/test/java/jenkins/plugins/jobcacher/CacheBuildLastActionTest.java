package jenkins.plugins.jobcacher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CacheBuildLastAction} to ensure it handles unmodifiable lists correctly.
 */
class CacheBuildLastActionTest {

    @Test
    void testAddCachesWithUnmodifiableInitialList() {
        // Create an ArbitraryFileCache for testing
        ArbitraryFileCache cache1 = new ArbitraryFileCache("path1", null, null);
        ArbitraryFileCache cache2 = new ArbitraryFileCache("path2", null, null);

        // Create CacheBuildLastAction with an unmodifiable singleton list
        // This simulates what happens when a pipeline uses a single cache definition
        List<Cache> unmodifiableList = Collections.singletonList(cache1);
        CacheBuildLastAction action = new CacheBuildLastAction(unmodifiableList);

        // This should NOT throw UnsupportedOperationException
        // Before the fix, this would fail because addAll() was called on the unmodifiable list
        action.addCaches(Collections.singletonList(cache2));

        // Verify both caches are present
        CacheProjectAction projectAction = (CacheProjectAction) action.getProjectActions().iterator().next();
        assertThat(projectAction.getCaches(), hasSize(2));
        assertThat(projectAction.getCaches(), containsInAnyOrder(cache1, cache2));
    }

    @Test
    void testAddCachesWithEmptyUnmodifiableList() {
        ArbitraryFileCache cache = new ArbitraryFileCache("path", null, null);

        // Create with empty unmodifiable list
        List<Cache> emptyList = Collections.emptyList();
        CacheBuildLastAction action = new CacheBuildLastAction(emptyList);

        // Should not throw
        action.addCaches(Collections.singletonList(cache));

        CacheProjectAction projectAction = (CacheProjectAction) action.getProjectActions().iterator().next();
        assertThat(projectAction.getCaches(), hasSize(1));
        assertThat(projectAction.getCaches(), contains(cache));
    }

    @Test
    void testAddCachesMultipleTimes() {
        ArbitraryFileCache cache1 = new ArbitraryFileCache("path1", null, null);
        ArbitraryFileCache cache2 = new ArbitraryFileCache("path2", null, null);
        ArbitraryFileCache cache3 = new ArbitraryFileCache("path3", null, null);

        // Start with unmodifiable list
        CacheBuildLastAction action = new CacheBuildLastAction(List.of(cache1));

        // Add caches multiple times (simulates multiple cache blocks in a pipeline)
        action.addCaches(List.of(cache2));
        action.addCaches(List.of(cache3));

        CacheProjectAction projectAction = (CacheProjectAction) action.getProjectActions().iterator().next();
        assertThat(projectAction.getCaches(), hasSize(3));
        assertThat(projectAction.getCaches(), containsInAnyOrder(cache1, cache2, cache3));
    }
}
