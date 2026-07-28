/*
 * The MIT License
 *
 * Copyright 2016 Peter Hayes.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.jobcacher;

import hudson.model.Action;
import hudson.model.Job;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * @author Peter Hayes
 */
public class CacheProjectAction implements Action {

    private final List<Cache> caches;

    public CacheProjectAction(List<Cache> caches) {
        this.caches = new ArrayList<>(caches);
    }

    @Override
    public String getIconFileName() {
        return "folder.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.CacheProjectAction_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "cache";
    }

    public Job<?, ?> getJob() {
        StaplerRequest2 request = Stapler.getCurrentRequest2();

        return request == null ? null : request.findAncestorObject(Job.class);
    }

    public List<Cache> getCaches() {
        return caches;
    }

    /**
     * Checks whether the current user is allowed to delete the caches of the ancestor job. Used by the user interface
     * to decide whether the deletion controls are rendered.
     *
     * @return true if so, false otherwise
     */
    public boolean isDeletable() {
        Job<?, ?> job = getJob();

        return job != null && job.hasPermission(Cache.DELETE_PERMISSION);
    }

    /**
     * Deletes all caches of the ancestor job, including caches whose configuration has been removed in the meantime,
     * and redirects back to the cache overview.
     */
    @RequirePOST
    public HttpResponse doDeleteAll(@AncestorInPath Job<?, ?> job) throws IOException, InterruptedException {
        if (job == null) {
            return HttpResponses.notFound();
        }

        job.checkPermission(Cache.DELETE_PERMISSION);

        CacheManager.deleteAll(GlobalItemStorage.get().getStorage(), job);

        return HttpResponses.redirectViaContextPath(job.getUrl() + "cache/");
    }
}
