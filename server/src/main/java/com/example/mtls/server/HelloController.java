package com.example.mtls.server;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public Map<String, String> hello(HttpServletRequest request) {
        X509Certificate[] certs = (X509Certificate[])
                request.getAttribute("jakarta.servlet.request.X509Certificate");

        String clientDN = (certs != null && certs.length > 0)
                ? certs[0].getSubjectX500Principal().getName()
                : "unknown";

        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Hello from mTLS server!");
        response.put("clientCN", clientDN);
        response.put("timestamp", Instant.now().toString());

        return response;
    }
}
