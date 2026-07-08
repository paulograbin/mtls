package com.example.mtls.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Runs on application startup and demonstrates both mTLS scenarios:
 *
 * Scenario 1: Client presents a valid certificate → server accepts → SUCCESS
 * Scenario 2: Client presents NO certificate → server rejects → FAILURE
 */
@Component
public class DemoRunner implements CommandLineRunner {

    private final RestTemplate mtlsRestTemplate;
    private final RestTemplate noMtlsRestTemplate;
    private final String serverUrl;

    public DemoRunner(
            @Qualifier("mtlsRestTemplate") RestTemplate mtlsRestTemplate,
            @Qualifier("noMtlsRestTemplate") RestTemplate noMtlsRestTemplate,
            @Value("${mtls.server-url}") String serverUrl) {
        this.mtlsRestTemplate = mtlsRestTemplate;
        this.noMtlsRestTemplate = noMtlsRestTemplate;
        this.serverUrl = serverUrl;
    }

    @Override
    public void run(String... args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              mTLS Demo - Client Application                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Calling server at: " + serverUrl);
        System.out.println();

        // --- Scenario 1: WITH mTLS (should succeed) ---
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  SCENARIO 1: WITH client certificate (mTLS)");
        System.out.println("  → Client presents: CN=demo-client, O=mTLS-Demo");
        System.out.println("  → Server validates against its truststore (CA: CN=Demo-CA)");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();

        try {
            String response = mtlsRestTemplate.getForObject(serverUrl, String.class);
            System.out.println("  ✅ SUCCESS! Server response:");
            System.out.println("     " + response);
        } catch (Exception e) {
            System.out.println("  ❌ UNEXPECTED FAILURE: " + e.getMessage());
        }

        System.out.println();
        System.out.println();

        // --- Scenario 2: WITHOUT mTLS (should fail) ---
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  SCENARIO 2: WITHOUT client certificate (no mTLS)");
        System.out.println("  → Client presents: NOTHING (no keystore configured)");
        System.out.println("  → Server requires client-auth=need → rejects connection");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();

        try {
            String response = noMtlsRestTemplate.getForObject(serverUrl, String.class);
            System.out.println("  ⚠️  UNEXPECTED SUCCESS: " + response);
        } catch (Exception e) {
            System.out.println("  ✅ EXPECTED FAILURE! Connection rejected by server.");
            System.out.println("     Exception: " + e.getClass().getSimpleName());
            System.out.println("     Message:   " + extractRootCause(e));
        }

        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Demo complete! mTLS enforcement is working correctly.");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
    }

    private String extractRootCause(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
