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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.ItemStorage;
import jenkins.plugins.itemstorage.ItemStorageDescriptor;
import jenkins.plugins.itemstorage.Messages;
import org.jenkinsci.plugins.variant.OptionalExtension;
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
public class S3ItemStorage extends ItemStorage<S3ObjectPath> {

    private final String credentialsId;
    private final String bucketName;
    private final String prefix;
    private final String region;

    @DataBoundConstructor
    public S3ItemStorage(String credentialsId, String bucketName, String prefix, String region) {
        this.credentialsId = credentialsId;
        this.bucketName = bucketName;
        this.prefix = prefix;
        this.region = region;
    }

    @SuppressWarnings("unused")
    public String getBucketName() {
        return bucketName;
    }

    @SuppressWarnings("unused")
    public String getPrefix() {
        return prefix;
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
        return new S3Profile(lookupCredentials(), null, region, prefix, null, false, true);
    }

    @OptionalExtension(requirePlugins = {"aws-java-sdk-minimal", "aws-credentials", "jackson2-api"})
    public static final class DescriptorImpl extends ItemStorageDescriptor<S3ObjectPath> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.S3ItemStorage_DisplayName();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String value) {
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                return new ListBoxModel();
            }
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .withAll(possibleCredentials());
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillRegionItems() {
            ListBoxModel model = new ListBoxModel();
            for (Regions r : Regions.values()) {
                model.add(r.getName(), r.getName());
            }
            return model;
        }
    }

    @OptionalExtension(requirePlugins = {"aws-java-sdk-minimal", "aws-credentials", "jackson2-api"})
    public static final class S3ItemListener extends ItemListener {

        @Override
        public void onDeleted(Item item) {
            S3ItemStorage s3Storage = lookupS3Storage();
            if (s3Storage == null) {
                return;
            }

            s3Storage.createS3Profile().delete(s3Storage.bucketName, item.getFullName());
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            S3ItemStorage s3Storage = lookupS3Storage();
            if (s3Storage == null) {
                return;
            }

            s3Storage.createS3Profile().rename(s3Storage.bucketName, oldFullName, newFullName);
        }

        private S3ItemStorage lookupS3Storage() {
            ItemStorage<?> storage = GlobalItemStorage.get().getStorage();

            if (storage instanceof S3ItemStorage) {
                return (S3ItemStorage) storage;
            } else {
                return null;
            }
        }
    }
}
