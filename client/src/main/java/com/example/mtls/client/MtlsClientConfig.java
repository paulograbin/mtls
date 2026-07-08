package com.example.mtls.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.http.HttpClient;
import java.security.KeyStore;

/**
 * Configures a RestTemplate with FULL mTLS:
 * - Keystore: holds the client's private key + certificate (presented to server)
 * - Truststore: holds the CA certificate (to verify the server's certificate)
 *
 * This RestTemplate will SUCCEED when calling the mTLS server because:
 * 1. It trusts the server (CA cert in truststore validates server cert)
 * 2. It presents a valid client certificate (from keystore, signed by same CA)
 */
@Configuration
public class MtlsClientConfig {

    @Value("${mtls.key-store}")
    private Resource keyStore;

    @Value("${mtls.key-store-password}")
    private String keyStorePassword;

    @Value("${mtls.trust-store}")
    private Resource trustStore;

    @Value("${mtls.trust-store-password}")
    private String trustStorePassword;

    @Bean("mtlsRestTemplate")
    public RestTemplate mtlsRestTemplate() throws Exception {
        // REVIEW #1: Resource leak — InputStream is never closed. Use try-with-resources.
        // Load the client keystore (contains client private key + certificate)
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(keyStore.getInputStream(), keyStorePassword.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyStorePassword.toCharArray());

        // REVIEW #1: Resource leak — InputStream is never closed. Use try-with-resources.
        // Load the truststore (contains CA certificate to verify server)
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(trustStore.getInputStream(), trustStorePassword.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        // Build SSLContext with both key material and trust material
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();

        return new RestTemplate(new JdkClientHttpRequestFactory(httpClient));
    }
}
