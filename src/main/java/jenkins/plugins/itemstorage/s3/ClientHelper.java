package jenkins.plugins.itemstorage.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import hudson.ProxyConfiguration;
import hudson.util.Secret;

import java.io.Serializable;

/**
 * Modification of the Jenkins S3 Plugin
 *
 * Stores settings to be used at a later time.
 */
public class ClientHelper implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final ProxyConfiguration proxy;
    private final String endpoint;
    private final String signerVersion;
    private final boolean pathStyleAccess;
    private final boolean parallelDownloads;

    private transient AWSCredentials credentials;
    private transient AmazonS3 client;

    public ClientHelper(AWSCredentials credentials, ProxyConfiguration proxy) {
        this(credentials, null, proxy);
    }

    public ClientHelper(AWSCredentials credentials, String region, ProxyConfiguration proxy) {
        this(credentials, null, region, proxy, null, false, true);
    }

    public ClientHelper(AWSCredentials credentials, String endpoint, String region, ProxyConfiguration proxy, String signerVersion, boolean pathStyleAccess, boolean parallelDownloads) {
        this.region = region;
        this.proxy = proxy;
        this.endpoint = endpoint;
        this.signerVersion = signerVersion;
        this.pathStyleAccess = pathStyleAccess;
        this.parallelDownloads = parallelDownloads;

        if (credentials != null) {
            this.accessKey = credentials.getAWSAccessKeyId();
            this.secretKey = credentials.getAWSSecretKey();
        } else {
            this.accessKey = null;
            this.secretKey = null;
        }
    }

    public boolean supportsParallelDownloads() {
        return parallelDownloads;
    }

    public synchronized AmazonS3 client() {
        if (client == null) {
            AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
            ClientConfiguration config = getClientConfiguration(proxy);

            if (getCredentials() != null) {
                builder.setCredentials(new AWSStaticCredentialsProvider(getCredentials()));
            }

            if (endpoint != null) {
                builder.setEndpointConfiguration(new EndpointConfiguration(endpoint, region));
                builder.setPathStyleAccessEnabled(pathStyleAccess);
                config.setSignerOverride(signerVersion);
            } else if (region != null) {
                builder.setRegion(region);
            }

            builder.setClientConfiguration(config);
            client = builder.build();
        }

        return client;
    }

    public static ClientConfiguration getClientConfiguration(ProxyConfiguration proxy) {
        ClientConfiguration clientConfiguration = new ClientConfiguration();

        if (shouldUseProxy(proxy, "s3.amazonaws.com")) {
            clientConfiguration.setProxyHost(proxy.name);
            clientConfiguration.setProxyPort(proxy.port);

            if (proxy.getUserName() != null) {
                clientConfiguration.setProxyUsername(proxy.getUserName());
                clientConfiguration.setProxyPassword(Secret.toString(proxy.getSecretPassword()));
            }
        }

        return clientConfiguration;
    }

    private static boolean shouldUseProxy(ProxyConfiguration proxy, String hostname) {
        if (proxy == null) {
            return false;
        }

        return proxy.getNoProxyHostPatterns().stream().noneMatch(p -> p.matcher(hostname).matches());
    }

    public synchronized AWSCredentials getCredentials() {
        if (credentials == null && accessKey != null && secretKey != null) {
            credentials = new BasicAWSCredentials(accessKey, secretKey);
        }
        return credentials;
    }
}
