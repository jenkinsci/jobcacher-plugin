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
import java.util.List;
import org.kohsuke.stapler.Stapler;

/**
 * @author Peter Hayes
 */
public class CacheProjectAction implements Action {

    private final List<Cache> caches;

    public CacheProjectAction(List<Cache> caches) {
        this.caches = caches;
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
        return Stapler.getCurrentRequest2().findAncestorObject(Job.class);
    }

    public List<Cache> getCaches() {
        return caches;
    }
}
