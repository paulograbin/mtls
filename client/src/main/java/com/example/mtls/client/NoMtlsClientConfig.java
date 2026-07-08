package com.example.mtls.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.http.HttpClient;
import java.security.KeyStore;

/**
 * Configures a RestTemplate WITHOUT client certificate:
 * - Truststore: holds the CA certificate (to verify the server's certificate)
 * - NO Keystore: does NOT present any client certificate
 *
 * This RestTemplate will FAIL when calling the mTLS server because:
 * 1. It trusts the server (CA cert validates server cert) ✓
 * 2. But it has NO client certificate to present ✗
 *
 * The server requires client-auth=need, so during the TLS handshake:
 *   → Server sends CertificateRequest
 *   → Client sends empty Certificate (no keystore loaded)
 *   → Server rejects with "bad_certificate" alert
 *   → Java throws SSLHandshakeException
 */
@Configuration
public class NoMtlsClientConfig {

    @Value("${mtls.trust-store}")
    private Resource trustStore;

    @Value("${mtls.trust-store-password}")
    private String trustStorePassword;

    @Bean("noMtlsRestTemplate")
    public RestTemplate noMtlsRestTemplate() throws Exception {
        // REVIEW #1: Resource leak — InputStream is never closed. Use try-with-resources.
        // Load ONLY the truststore (to verify server cert)
        // NO keystore → no client certificate will be presented
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(trustStore.getInputStream(), trustStorePassword.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        // Build SSLContext with trust material ONLY — no key material
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);  // null KeyManagers = no client cert

        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();

        return new RestTemplate(new JdkClientHttpRequestFactory(httpClient));
    }
}
