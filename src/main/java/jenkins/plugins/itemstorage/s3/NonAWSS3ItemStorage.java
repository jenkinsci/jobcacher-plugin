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

import static hudson.Util.fixEmptyAndTrim;

import com.amazonaws.auth.SignerFactory;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.NonNull;
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

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * S3 implementation of the Item Storage extension point.
 *
 * @author Peter Hayes
 */
public class NonAWSS3ItemStorage extends ItemStorage<S3ObjectPath> {

    private static final long serialVersionUID = 1L;

    private final String credentialsId;
    private final String bucketName;
    private final String endpoint;
    private final String region;
    private final String signerVersion;
    private final boolean pathStyleAccess;
    private final boolean parallelDownloads;

    @DataBoundConstructor
    public NonAWSS3ItemStorage(String credentialsId,
                               String bucketName,
                               String endpoint,
                               String region,
                               String signerVersion,
                               boolean pathStyleAccess,
                               boolean parallelDownloads) {
        this.credentialsId = fixEmptyAndTrim(credentialsId);
        this.bucketName = fixEmptyAndTrim(bucketName);
        this.endpoint = fixEmptyAndTrim(endpoint);
        this.region = fixEmptyAndTrim(region);
        this.signerVersion = fixEmptyAndTrim(signerVersion);
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
        return new S3ObjectPath(createS3Profile(), bucketName, region, item.getFullName(), path);
    }

    @Override
    public S3ObjectPath getObjectPathForBranch(Item item, String path, String branch) {
        String branchPath = new File(item.getFullName()).getParent() + "/" + branch;

        return new S3ObjectPath(createS3Profile(), bucketName, region, branchPath, path);
    }

    private AmazonWebServicesCredentials lookupCredentials() {
        return (credentialsId == null) ? null : CredentialsMatchers.firstOrNull(
                possibleCredentials(),
                CredentialsMatchers.withId(credentialsId));
    }

    private static List<AmazonWebServicesCredentials> possibleCredentials() {
        return CredentialsProvider.lookupCredentials(AmazonWebServicesCredentials.class, Jenkins.get(),
                ACL.SYSTEM, Collections.emptyList());
    }

    private S3Profile createS3Profile() {
        return new S3Profile(lookupCredentials(), endpoint, region, signerVersion, pathStyleAccess, parallelDownloads);
    }

    @Extension(optional = true)
    public static final class DescriptorImpl extends ItemStorageDescriptor<S3ObjectPath> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.NonAWSS3ItemStorage_DisplayName();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String value) {
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                return new ListBoxModel();
            }
            return new StandardListBoxModel()
                    .withAll(possibleCredentials());
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillSignerVersionItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("None", "");
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
            if (s3Storage == null) {
                return;
            }

            s3Storage.createS3Profile().delete(s3Storage.bucketName, item.getFullName());
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            NonAWSS3ItemStorage s3Storage = lookupS3Storage();
            if (s3Storage == null) {
                return;
            }

            s3Storage.createS3Profile().rename(s3Storage.bucketName, oldFullName, newFullName);
        }

        private NonAWSS3ItemStorage lookupS3Storage() {
            ItemStorage<?> storage = GlobalItemStorage.get().getStorage();
            if (storage instanceof NonAWSS3ItemStorage) {
                return (NonAWSS3ItemStorage) storage;
            } else {
                return null;
            }
        }
    }
}
