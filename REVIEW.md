# Code Review — mTLS Demo Project

## Implementation Issues

### MEDIUM

#### 1. Resource leak in client config classes

**Files:**
- `client/src/main/java/com/example/mtls/client/MtlsClientConfig.java` (lines 44, 51)
- `client/src/main/java/com/example/mtls/client/NoMtlsClientConfig.java` (line 44)

`InputStream` from `keyStore.getInputStream()` / `trustStore.getInputStream()` is never closed. `KeyStore.load()` does NOT close the stream per its API contract. Should use try-with-resources:

```java
try (var is = keyStore.getInputStream()) {
    ks.load(is, keyStorePassword.toCharArray());
}
```

#### 2. No signal trap in `run-demo.sh` (line 39)

If Ctrl+C is pressed after the server starts, the background Java process is orphaned. Add after `SERVER_PID=$!`:

```bash
trap 'kill $SERVER_PID 2>/dev/null; wait $SERVER_PID 2>/dev/null' EXIT INT TERM
```

#### 3. `${user.dir}` path resolution is fragile

**Files:**
- `server/src/main/resources/application.yml` (lines 5-8)
- `client/src/main/resources/application.yml` (lines 12-15)

Uses `file:${user.dir}/../certs/...` which only works when jars are launched from their own directory (as `run-demo.sh` does). Running from any other directory breaks it.

### LOW

#### 4. Unused `server.port: 8080` in client config

**File:** `client/src/main/resources/application.yml` (line 2)

Has no effect since `web-application-type: none` is set. Dead configuration that could confuse readers.

#### 5. Step numbering mismatch in `run-demo.sh`

**File:** `run-demo.sh` (lines 12, 33, 58, 67)

Section comments say "Step 3/4/5" but echo output says "Step 4/5/6". Cosmetic but confusing.

#### 6. `extractRootCause()` edge cases

**File:** `client/src/main/java/com/example/mtls/client/DemoRunner.java` (lines 84-89)

- `cause.getMessage()` can return `null` — output would print literal "null"
- Circular cause chains (extremely rare) would loop forever

---

## README Issues

### Inaccuracies

#### 7. CN ordering wrong in expected output

**File:** `README.md` (lines 61, 272)

Shows `"clientCN":"CN=demo-client,O=mTLS-Demo"` but Java's `X500Principal.getName()` uses RFC 2253 format which reverses the order. Actual output is `"O=mTLS-Demo,CN=demo-client"` (confirmed at runtime).

#### 8. Exception class wrong in expected output

**File:** `README.md` (line 68)

Shows `Exception: SSLHandshakeException` but Spring's `RestTemplate` wraps it in `ResourceAccessException`. Actual output is `Exception: ResourceAccessException` (confirmed at runtime). The root cause message is correct.

### Missing Content

#### 9. No PKCS12 vs JKS explanation

Should explain why PKCS12 was chosen:
- JKS is Java-proprietary, deprecated since Java 9
- PKCS12 is industry standard (RFC 7292), usable across platforms
- Spring Boot logs a warning if JKS is used

#### 10. No `.crt/.key` → `.p12` transformation explanation

Should explain:
- OpenSSL generates `.key` (private key) and `.crt` (certificate) as separate PEM files
- Java's `KeyStore` API expects a single file containing both
- `openssl pkcs12 -export` packages them together into `.p12`

#### 11. CSR files not documented

`server.csr` and `client.csr` exist in `certs/` but aren't in the file table. CSRs are important: they contain the public key + requested identity, sent to the CA for signing without exposing the private key.

#### 12. No production guidance section

Missing:
- **Certificate Rotation**: How to renew certs before expiry without downtime
- **Revocation**: CRL / OCSP for invalidating compromised certs
- **Real CAs**: Let's Encrypt, Vault PKI, corporate CAs
- **Intermediate CAs**: Root → intermediate → leaf chain
- **Private key protection**: Strong passphrases, HSMs (not `changeit` / `-nodes`)

#### 13. No troubleshooting scenarios

Missing common errors:
- **Expired certs**: `NotAfterException` / "certificate has expired"
- **Hostname/SAN mismatch**: `SSLPeerUnverifiedException`
- **Wrong CA**: "PKIX path building failed: unable to find valid certification path"
- **Password mismatch**: `UnrecoverableKeyException`
- **Key/cert mismatch**: "private key does not match public key"

#### 14. `client-auth` modes need expansion

Should explain `want` mode behavior: server requests cert but proceeds without one. Use case: optional cert auth with password fallback.

#### 15. Why mTLS exists (motivation)

Should note: regular TLS authenticates the client at the application layer (passwords, API keys, OAuth). mTLS pushes it to the transport layer — stronger guarantees, no credential to steal/replay.

#### 16. Additional debug commands missing

- `openssl s_client -connect localhost:8443 -state` (state transitions)
- `openssl pkcs12 -in file.p12 -info -nodes -passin pass:changeit` (view PKCS12 contents)
- `keytool -list -keystore file.p12 -storetype PKCS12 -storepass changeit -v` (verbose)
- Wireshark: filter `tls.handshake`

#### 17. TLS 1.3 simplification note

Handshake diagram simplifies TLS 1.3 (server messages come in one encrypted flight, not separate round-trips). A "(simplified for clarity)" note would help advanced readers.
