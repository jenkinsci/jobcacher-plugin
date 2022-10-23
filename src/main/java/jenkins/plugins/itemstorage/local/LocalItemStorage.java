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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.ItemStorage;
import jenkins.plugins.itemstorage.ItemStorageDescriptor;
import jenkins.plugins.itemstorage.Messages;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Implementation of Item Storage that stores data on the Jenkins controller within the existing job folder or in custom path.
 *
 * @author Peter Hayes
 */
public class LocalItemStorage extends ItemStorage<LocalObjectPath> {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(LocalItemStorage.class.getName());

    private String root;

    @DataBoundConstructor
    public LocalItemStorage() {
    }

    @DataBoundSetter
    public void setRoot(String root) {
        this.root = root;
    }

    public String getRoot() {
        return root;
    }

    @Override
    public LocalObjectPath getObjectPath(Item item, String path) {
        return new LocalObjectPath(getItemRoot(item).child(path));
    }

    @Override
    public LocalObjectPath getObjectPathForBranch(Item item, String path, String branch) {
        FilePath parent = getItemRoot(item).getParent();
        if (parent == null) {
            return null;
        }

        FilePath branchPath = parent.child(branch);
        return new LocalObjectPath(branchPath.child(path));
    }

    private FilePath getItemRoot(Item item) {
        if (root != null) {
            return getItemRoot(item.getFullName());
        } else {
            return new FilePath(item.getRootDir());
        }
    }

    private FilePath getItemRoot(String itemFullName) {
        return new FilePath(new File(root)).child(itemFullName);
    }

    @Extension
    public static final class DescriptorImpl extends ItemStorageDescriptor<LocalObjectPath> {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.LocalItemStorage_DisplayName();
        }
    }

    @Extension(optional = true)
    public static final class LocalItemListener extends ItemListener {

        @Override
        public void onDeleted(Item item) {
            LocalItemStorage storage = lookupStorage();
            if (storage == null || storage.root == null) {
                return;
            }

            try {
                storage.getItemRoot(item).deleteRecursive();
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                LOG.warning("failed to delete item storage for " + item.getFullName() + ": " + e.getMessage());
            }
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            LocalItemStorage storage = lookupStorage();
            if (storage == null || storage.root == null) {
                return;
            }

            try {
                storage.getItemRoot(oldFullName).renameTo(storage.getItemRoot(newFullName));
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                LOG.warning("failed to rename item storage for " + item.getFullName() + ": " + e.getMessage());
            }
        }

        private static LocalItemStorage lookupStorage() {
            ItemStorage<?> storage = GlobalItemStorage.get().getStorage();

            if (storage instanceof LocalItemStorage) {
                return (LocalItemStorage) storage;
            } else {
                return null;
            }
        }

    }

}
