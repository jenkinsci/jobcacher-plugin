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

package jenkins.plugins.itemstorage.local;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Item;
import jenkins.plugins.itemstorage.ItemStorage;
import jenkins.plugins.itemstorage.ItemStorageDescriptor;
import jenkins.plugins.itemstorage.Messages;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * Implementation of Item Storage that stores data on the Jenkins master within
 * the existing job folder.
 *
 * @author Peter Hayes
 */
public class LocalItemStorage extends ItemStorage<LocalObjectPath> {
    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public LocalItemStorage() {
    }

    @Override
    public LocalObjectPath getObjectPath(Item item, String path) {
        return new LocalObjectPath(new FilePath(item.getRootDir()).child(path));
    }

    @Override
    public LocalObjectPath getObjectPathForBranch(Item item, String path, String branch) {
        FilePath parent = new FilePath(item.getRootDir()).getParent();
        if (parent == null)
            return null;
        FilePath branchPath = parent.child(branch);
        return new LocalObjectPath(branchPath.child(path));
    }

    @Extension
    public static final class DescriptorImpl extends ItemStorageDescriptor<LocalObjectPath> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.LocalItemStorage_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return super.getHelpFile();
        }
    }
}
