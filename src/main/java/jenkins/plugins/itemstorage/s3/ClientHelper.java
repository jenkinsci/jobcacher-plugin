package jenkins.plugins.itemstorage.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import hudson.ProxyConfiguration;

import java.io.Serializable;
import java.util.regex.Pattern;

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

    private transient AWSCredentials credentials;
    private transient AmazonS3 client;

    public ClientHelper(AWSCredentials credentials, ProxyConfiguration proxy) {
        this(credentials, null, proxy);
    }

    public ClientHelper(AWSCredentials credentials, String region, ProxyConfiguration proxy) {
        this.region = region;
        this.proxy = proxy;

        if (credentials != null) {
            this.accessKey = credentials.getAWSAccessKeyId();
            this.secretKey = credentials.getAWSSecretKey();
        } else {
            this.accessKey = null;
            this.secretKey = null;
        }
    }

    public synchronized AmazonS3 client()
    {
        if (client == null) {

            if (getCredentials() == null) {
                client = AmazonS3ClientBuilder.standard()
                            .withRegion(region)
                            .withClientConfiguration(getClientConfiguration(proxy))
                            .build();
            } else {
                client = AmazonS3ClientBuilder.standard()
                            .withRegion(region)
                            .withCredentials(new AWSStaticCredentialsProvider(getCredentials()))
                            .withClientConfiguration(getClientConfiguration(proxy))
                            .build();
            }
        }

        return client;
    }

    private static Region getRegionFromString(String regionName) {
        // In 0.7, selregion comes from Regions#name
        Region region = RegionUtils.getRegion(regionName);

        // In 0.6, selregion comes from Regions#valueOf
        if (region == null) {
            region = RegionUtils.getRegion(Regions.valueOf(regionName).getName());
        }

        return region;
    }

    public static ClientConfiguration getClientConfiguration(ProxyConfiguration proxy) {
        final ClientConfiguration clientConfiguration = new ClientConfiguration();

        if (shouldUseProxy(proxy, "s3.amazonaws.com")) {
            clientConfiguration.setProxyHost(proxy.name);
            clientConfiguration.setProxyPort(proxy.port);
            if (proxy.getUserName() != null) {
                clientConfiguration.setProxyUsername(proxy.getUserName());
                clientConfiguration.setProxyPassword(proxy.getPassword());
            }
        }

        return clientConfiguration;
    }

    private static boolean shouldUseProxy(ProxyConfiguration proxy, String hostname) {
        if(proxy == null) {
            return false;
        }

        boolean shouldProxy = true;
        for(Pattern p : proxy.getNoProxyHostPatterns()) {
            if(p.matcher(hostname).matches()) {
                shouldProxy = false;
                break;
            }
        }

        return shouldProxy;
    }

    public synchronized  AWSCredentials getCredentials() {
        if (credentials == null && accessKey != null && secretKey != null) {
            credentials = new BasicAWSCredentials(accessKey, secretKey);
        }
        return credentials;
    }
}
