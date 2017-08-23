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
import com.amazonaws.auth.SignerFactory;
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
import java.util.Collections;
import java.util.List;

/**
 * S3 implementation of the Item Storage extension point.
 *
 * @author Peter Hayes
 */
public class NonAWSS3ItemStorage extends ItemStorage<S3ObjectPath> {
    private String credentialsId;
    private String bucketName;
    private String endpoint;
    private String region;
    private String signerVersion;
    private boolean pathStyleAccess;
    private boolean parallelDownloads;

    @DataBoundConstructor
    public NonAWSS3ItemStorage(String credentialsId, String bucketName, String endpoint, String region, String signerVersion, boolean pathStyleAccess, boolean parallelDownloads) {
        this.credentialsId = credentialsId;
        this.bucketName = bucketName;
        this.endpoint = endpoint;
        this.region = region;
        this.signerVersion = signerVersion;
        this.pathStyleAccess = pathStyleAccess;
        this.parallelDownloads = parallelDownloads;
    }

    @SuppressWarnings("unused")
    public String getBucketName() {
        return bucketName;
    }

    @SuppressWarnings("unused")
    public String getEndpoint() {
        return endpoint;
    }

    @SuppressWarnings("unused")
    public String getRegion() {
        return region;
    }

    @SuppressWarnings("unused")
    public String getCredentialsId() {
        return credentialsId;
    }

    @SuppressWarnings("unused")
    public String getSignerVersion() {
        return signerVersion;
    }

    @SuppressWarnings("unused")
    public boolean getPathStyleAccess() {
        return pathStyleAccess;
    }

    @SuppressWarnings("unused")
    public boolean getParallelDownloads() {
        return parallelDownloads;
    }

    @Override
    public S3ObjectPath getObjectPath(Item item, String path) {
        S3Profile profile = new S3Profile(lookupCredentials(), endpoint, signerVersion, pathStyleAccess, parallelDownloads, 5, 5L);

        return new S3ObjectPath(profile, bucketName, region, item.getFullName(), path);
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
            return Messages.NonAWSS3ItemStorage_DisplayName();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String value) {
            if (!Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER)) {
                return new ListBoxModel();
            }
            return new StandardListBoxModel()
                .withAll(possibleCredentials());
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillSignerVersionItems() {
            final ListBoxModel model = new ListBoxModel();
            model.add("Version 4", SignerFactory.VERSION_FOUR_SIGNER);
            model.add("Version 3", SignerFactory.VERSION_THREE_SIGNER);
            model.add("Version 2", "S3SignerType");

            return model;
        }
    }

    @Extension(optional = true)
    public static final class S3ItemListener extends ItemListener {
        @Override
        public void onDeleted(Item item) {
            NonAWSS3ItemStorage s3Storage = lookupS3Storage();

            if (s3Storage == null) return;

            S3Profile profile = new S3Profile(s3Storage.lookupCredentials(), s3Storage.getEndpoint(), s3Storage.getSignerVersion(), s3Storage.getPathStyleAccess(), s3Storage.getParallelDownloads(), 5, 5L);
            profile.delete(s3Storage.bucketName, item.getFullName());
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            NonAWSS3ItemStorage s3Storage = lookupS3Storage();

            if (s3Storage == null) return;

            S3Profile profile = new S3Profile(s3Storage.lookupCredentials(), s3Storage.getEndpoint(), s3Storage.getSignerVersion(), s3Storage.getPathStyleAccess(), s3Storage.getParallelDownloads(), 5, 5L);
            profile.rename(s3Storage.bucketName, oldFullName, newFullName);
        }

        private NonAWSS3ItemStorage lookupS3Storage() {
            ItemStorage storage = GlobalItemStorage.get().getStorage();

            if (storage instanceof NonAWSS3ItemStorage) {
                return (NonAWSS3ItemStorage) storage;
            } else {
                return null;
            }
        }
    }
}
