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

package jenkins.plugins.itemstorage.s3;

import com.amazonaws.regions.Regions;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.ItemStorage;
import jenkins.plugins.itemstorage.ItemStorageDescriptor;
import jenkins.plugins.itemstorage.Messages;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * S3 implementation of the Item Storage extension point.
 *
 * @author Peter Hayes
 */
public class S3ItemStorage extends ItemStorage<S3ObjectPath> {
    private String credentialsId;
    private String bucketName;
    private String region;

    @DataBoundConstructor
    public S3ItemStorage(String credentialsId, String bucketName, String region) {
        this.credentialsId = credentialsId;
        this.bucketName = bucketName;
        this.region = region;
    }

    @SuppressWarnings("unused")
    public String getBucketName() {
        return bucketName;
    }

    @SuppressWarnings("unused")
    public String getRegion() {
        return region;
    }

    @SuppressWarnings("unused")
    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public S3ObjectPath getObjectPath(Item item, String path) {
        S3Profile profile = new S3Profile(lookupCredentials(), null, null, false, true, 5, 5L);

        return new S3ObjectPath(profile, bucketName, region, item.getFullName(), path);
    }

    @Override
    public S3ObjectPath getObjectPathForBranch(Item item, String path, String branch) {
        S3Profile profile = new S3Profile(lookupCredentials(), null, null, false, true, 5, 5L);
        String branchPath = new File(item.getFullName()).getParent() + "/" + branch;

        return new S3ObjectPath(profile, bucketName, region, branchPath, path);
    }

    private AmazonWebServicesCredentials lookupCredentials() {
        return (credentialsId == null) ? null : CredentialsMatchers.firstOrNull(
                possibleCredentials(),
                CredentialsMatchers.withId(credentialsId));
    }

    private static List<AmazonWebServicesCredentials> possibleCredentials() {
        return CredentialsProvider.lookupCredentials(AmazonWebServicesCredentials.class, Jenkins.getInstance(),
                ACL.SYSTEM, Collections.<DomainRequirement>emptyList());
    }

    @Extension(optional = true)
    public static final class DescriptorImpl extends ItemStorageDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.S3ItemStorage_DisplayName();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String value) {
            if (!Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER)) {
                return new ListBoxModel();
            }
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withAll(possibleCredentials());
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillRegionItems() {
            final ListBoxModel model = new ListBoxModel();
            for (Regions r : Regions.values()) {
                model.add(r.getName(), r.getName());
            }
            return model;
        }
    }

    @Extension(optional = true)
    public static final class S3ItemListener extends ItemListener {
        @Override
        public void onDeleted(Item item) {
            S3ItemStorage s3Storage = lookupS3Storage();

            if (s3Storage == null) return;

            S3Profile profile = new S3Profile(s3Storage.lookupCredentials(), null, null, false, true, 5, 5L);
            profile.delete(s3Storage.bucketName, item.getFullName());
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            S3ItemStorage s3Storage = lookupS3Storage();

            if (s3Storage == null) return;

            S3Profile profile = new S3Profile(s3Storage.lookupCredentials(), null, null, false, true, 5, 5L);
            profile.rename(s3Storage.bucketName, oldFullName, newFullName);
        }

        private S3ItemStorage lookupS3Storage() {
            ItemStorage storage = GlobalItemStorage.get().getStorage();

            if (storage instanceof S3ItemStorage) {
                return (S3ItemStorage) storage;
            } else {
                return null;
            }
        }
    }
}
