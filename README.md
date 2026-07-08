# Mutual TLS (mTLS) Demo — Two Java Spring Boot Apps

This project demonstrates **mutual TLS authentication** between two Java applications.
One call succeeds (with proper client certificate), one call fails (without it).

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [How Certificates Work](#how-certificates-work)
3. [What is mTLS?](#what-is-mtls)
4. [The TLS Handshake — Step by Step](#the-tls-handshake--step-by-step)
5. [Why It Fails Without a Client Certificate](#why-it-fails-without-a-client-certificate)
6. [Project Files Explained](#project-files-explained)
7. [How to Inspect and Debug](#how-to-inspect-and-debug)

---

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- OpenSSL
- `keytool` (included with JDK)

### Run Everything

```bash
bash run-demo.sh
```

Or step by step:

```bash
# 1. Generate certificates
bash certs/generate-certs.sh

# 2. Build
(cd server && mvn package -DskipTests)
(cd client && mvn package -DskipTests)

# 3. Start the server (in one terminal)
cd server && java -jar target/mtls-server.jar

# 4. Run the client (in another terminal)
cd client && java -jar target/mtls-client.jar
```

### Expected Output

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  SCENARIO 1: WITH client certificate (mTLS)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  ✅ SUCCESS! Server response:
     {"message":"Hello from mTLS server!","clientCN":"CN=demo-client,O=mTLS-Demo","timestamp":"..."}


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  SCENARIO 2: WITHOUT client certificate (no mTLS)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  ✅ EXPECTED FAILURE! Connection rejected by server.
     Exception: SSLHandshakeException
     Message:   Received fatal alert: bad_certificate
```

---

## How Certificates Work

### What is a Certificate?

A digital certificate is a file that binds an **identity** (like a hostname or a person's name)
to a **public key**, and that binding is **signed** by a trusted authority (CA).

Think of it like a passport:
- The passport has your **name** (identity)
- It has your **photo** (public key — something that uniquely identifies you)
- It's **stamped by the government** (CA signature — proving it's legitimate)

### The Certificate Contents (our actual server cert)

When you run `openssl x509 -in certs/server.crt -text -noout`, you'll see:

```
Certificate:
    Data:
        Issuer:  CN=Demo-CA, O=mTLS-Demo          ← WHO signed it (our CA)
        Subject: CN=localhost, O=mTLS-Demo         ← WHO it belongs to (the server)
        Validity:
            Not Before: Jul  7 2026               ← When it becomes valid
            Not After:  Jul  7 2027               ← When it expires
        Subject Public Key Info:
            RSA Public Key: (2048 bit)            ← The server's public key
        X509v3 extensions:
            Subject Alternative Name:
                DNS:localhost, IP:127.0.0.1       ← What hostnames it's valid for
    Signature Algorithm: sha256WithRSAEncryption  ← How the CA signed it
    Signature: (hex bytes)                        ← The CA's digital signature
```

### Public/Private Key Pairs

Every certificate involves a **key pair**:

```
┌─────────────────────────────────────────────────────────┐
│  Private Key (server.key)         Public Key            │
│  ─────────────────────           ────────────           │
│  • NEVER leaves the server       • Inside the cert      │
│  • Used to PROVE identity        • Shared with everyone │
│  • Used to DECRYPT               • Used to ENCRYPT      │
│  • Only the owner has it         • Anyone can have it   │
└─────────────────────────────────────────────────────────┘
```

In our project:
- `server.key` — server's private key (stays on server, loaded in `server-keystore.p12`)
- `server.crt` — server's certificate with public key (sent to clients during handshake)
- `client.key` — client's private key (stays on client, loaded in `client-keystore.p12`)
- `client.crt` — client's certificate with public key (sent to server during mTLS)

### Chain of Trust

```
        ┌──────────────────┐
        │    Demo-CA       │  ← Self-signed Certificate Authority
        │  (ca.key/ca.crt) │     The "root of trust"
        └────────┬─────────┘
                 │ signs
       ┌─────────┴─────────┐
       │                    │
       ▼                    ▼
┌──────────────┐    ┌──────────────┐
│  Server Cert │    │  Client Cert │
│ CN=localhost │    │ CN=demo-client│
│(server.crt)  │    │(client.crt)  │
└──────────────┘    └──────────────┘
```

Both the server cert and client cert are signed by the **same CA**.
This means:
- The **server truststore** (`server-truststore.p12`) contains `ca.crt` → it can verify any cert signed by Demo-CA
- The **client truststore** (`client-truststore.p12`) contains `ca.crt` → it can verify the server's cert

### Keystore vs Truststore

| File | Contains | Purpose |
|------|----------|---------|
| `server-keystore.p12` | Server's private key + cert | **"This is who I am"** — presented during TLS handshake |
| `server-truststore.p12` | CA certificate | **"I trust certs signed by this CA"** — validates incoming client certs |
| `client-keystore.p12` | Client's private key + cert | **"This is who I am"** — presented to server for mTLS |
| `client-truststore.p12` | CA certificate | **"I trust certs signed by this CA"** — validates the server's cert |

---

## What is mTLS?

### Regular TLS (one-way)

In normal HTTPS, only the **server proves its identity** to the client:

```
Client: "Hi, I want to connect to https://google.com"
Server: "Here's my certificate: CN=google.com, signed by DigiCert"
Client: "I trust DigiCert (it's in my truststore). Certificate is valid. ✓"
        → Encrypted connection established
```

The server never asks "who are you?" — anyone can connect.

### Mutual TLS (two-way)

In mTLS, **both sides prove their identity**:

```
Client: "Hi, I want to connect to https://localhost:8443"
Server: "Here's my certificate: CN=localhost, signed by Demo-CA"
Client: "I trust Demo-CA (it's in my truststore). Server cert valid. ✓"
Server: "Now show ME your certificate." (because client-auth: need)
Client: "Here's my certificate: CN=demo-client, signed by Demo-CA"
Server: "I trust Demo-CA (it's in my truststore). Client cert valid. ✓"
        → Encrypted connection established
```

If the client has no certificate (or one not signed by the CA the server trusts):

```
Server: "Now show ME your certificate."
Client: "I... don't have one."
Server: "REJECTED. 🚫 bad_certificate"
        → Connection terminated
```

---

## The TLS Handshake — Step by Step

Here's exactly what happens when our client connects to `https://localhost:8443/hello`,
using the **actual values from this project**:

### Successful Handshake (with mTLS)

```
┌────────────────┐                           ┌────────────────┐
│  mtls-client   │                           │  mtls-server   │
│  (port 8080)   │                           │  (port 8443)   │
└───────┬────────┘                           └───────┬────────┘
        │                                            │
        │  1. ClientHello                            │
        │  ─────────────────────────────────────►    │
        │  "I support TLS 1.3, these cipher suites:  │
        │   TLS_AES_256_GCM_SHA384,                  │
        │   TLS_AES_128_GCM_SHA256, ..."             │
        │                                            │
        │  2. ServerHello                            │
        │  ◄─────────────────────────────────────    │
        │  "Let's use TLS 1.3 with                   │
        │   TLS_AES_256_GCM_SHA384"                  │
        │                                            │
        │  3. Server Certificate                     │
        │  ◄─────────────────────────────────────    │
        │  Certificate: CN=localhost, O=mTLS-Demo    │
        │  Signed by: CN=Demo-CA, O=mTLS-Demo       │
        │  SAN: DNS:localhost, IP:127.0.0.1          │
        │                                            │
        │  Client checks:                            │
        │  • Is CN/SAN matching "localhost"? ✓       │
        │  • Is it signed by CA in my truststore     │
        │    (client-truststore.p12)? ✓              │
        │  • Is it expired? No ✓                     │
        │                                            │
        │  4. CertificateRequest                     │
        │  ◄─────────────────────────────────────    │
        │  "I need YOUR certificate.                 │
        │   I trust CAs: CN=Demo-CA"                 │
        │                                            │
        │  5. Client Certificate                     │
        │  ─────────────────────────────────────►    │
        │  Certificate: CN=demo-client, O=mTLS-Demo  │
        │  Signed by: CN=Demo-CA, O=mTLS-Demo       │
        │                                            │
        │  6. CertificateVerify                      │
        │  ─────────────────────────────────────►    │
        │  (Client signs a hash with its private key │
        │   from client-keystore.p12, proving it     │
        │   owns the private key for CN=demo-client) │
        │                                            │
        │  Server checks:                            │
        │  • Is cert signed by CA in my truststore   │
        │    (server-truststore.p12)? ✓              │
        │  • Does CertificateVerify match? ✓         │
        │  • Is it expired? No ✓                     │
        │                                            │
        │  7. Finished (both sides)                  │
        │  ◄────────────────────────────────────►    │
        │  "Handshake complete. Encrypted channel    │
        │   established using session keys."         │
        │                                            │
        │  8. Application Data                       │
        │  ─────────────────────────────────────►    │
        │  GET /hello HTTP/1.1 (encrypted)           │
        │                                            │
        │  ◄─────────────────────────────────────    │
        │  200 OK                                    │
        │  {"message":"Hello from mTLS server!",     │
        │   "clientCN":"CN=demo-client,O=mTLS-Demo"} │
        │                                            │
```

### Failed Handshake (without client certificate)

```
┌────────────────┐                           ┌────────────────┐
│  mtls-client   │                           │  mtls-server   │
│  (no keystore) │                           │  (port 8443)   │
└───────┬────────┘                           └───────┬────────┘
        │                                            │
        │  1. ClientHello                            │
        │  ─────────────────────────────────────►    │
        │                                            │
        │  2. ServerHello                            │
        │  ◄─────────────────────────────────────    │
        │                                            │
        │  3. Server Certificate                     │
        │  ◄─────────────────────────────────────    │
        │  (Client validates server cert — OK ✓)     │
        │                                            │
        │  4. CertificateRequest                     │
        │  ◄─────────────────────────────────────    │
        │  "I need YOUR certificate."                │
        │                                            │
        │  5. Client Certificate (EMPTY)             │
        │  ─────────────────────────────────────►    │
        │  (No keystore loaded → sends empty cert)   │
        │                                            │
        │  6. Server checks:                         │
        │  • client-auth=need but no cert received!  │
        │                                            │
        │  7. Alert: bad_certificate (fatal)         │
        │  ◄─────────────────────────────────────    │
        │  "Connection REJECTED"                     │
        │                                            │
        │  8. Connection closed                      │
        │  ────────── ✗ ──────────                   │
        │                                            │
        │  Java throws:                              │
        │  javax.net.ssl.SSLHandshakeException:      │
        │    Received fatal alert: bad_certificate   │
        │                                            │
```

---

## Why It Fails Without a Client Certificate

The failure is by design. Here's the chain of events:

1. **Server configuration** (`application.yml`):
   ```yaml
   server.ssl.client-auth: need
   ```
   This tells the embedded Tomcat: "Reject any connection that doesn't present a valid client certificate."

2. **During the TLS handshake**, the server sends a `CertificateRequest` message.

3. **The client without a keystore** (`NoMtlsClientConfig.java`):
   ```java
   sslContext.init(null, tmf.getTrustManagers(), null);
   //           ^^^^
   //           No KeyManagers = no client certificate to present
   ```

4. **The client sends an empty Certificate message** to the server.

5. **The server enforces `need`** — it will not proceed without a valid client cert.
   It sends a `fatal alert: bad_certificate` and closes the connection.

6. **Java's SSL layer** catches this alert and throws:
   ```
   javax.net.ssl.SSLHandshakeException: Received fatal alert: bad_certificate
   ```

### What if we used `client-auth: want` instead of `need`?

| Mode | Behavior |
|------|----------|
| `need` | Server **requires** a client cert. No cert = connection rejected. |
| `want` | Server **requests** a client cert but allows connections without one. |
| `none` | Server never asks for a client cert (regular one-way TLS). |

We use `need` to demonstrate strict mTLS enforcement.

---

## Project Files Explained

### Certificate Files (generated by `certs/generate-certs.sh`)

| File | What it is | Who uses it |
|------|------------|-------------|
| `ca.key` | CA's private key (used only during cert generation) | `generate-certs.sh` |
| `ca.crt` | CA's certificate (the root of trust) | Both truststores |
| `server.key` | Server's private key | Packaged into `server-keystore.p12` |
| `server.crt` | Server's certificate (CN=localhost) | Packaged into `server-keystore.p12` |
| `client.key` | Client's private key | Packaged into `client-keystore.p12` |
| `client.crt` | Client's certificate (CN=demo-client) | Packaged into `client-keystore.p12` |
| `server-keystore.p12` | Server's identity (private key + cert) | Server app |
| `server-truststore.p12` | CAs the server trusts (validates client certs) | Server app |
| `client-keystore.p12` | Client's identity (private key + cert) | Client app (mTLS call) |
| `client-truststore.p12` | CAs the client trusts (validates server cert) | Client app (both calls) |

### Server App

| File | Role |
|------|------|
| `server/src/main/resources/application.yml` | Configures port 8443, SSL, and `client-auth: need` |
| `ServerApplication.java` | Spring Boot entry point |
| `HelloController.java` | `GET /hello` — returns JSON with the client's certificate CN |

### Client App

| File | Role |
|------|------|
| `client/src/main/resources/application.yml` | Points to keystores and server URL |
| `ClientApplication.java` | Spring Boot entry point |
| `MtlsClientConfig.java` | Creates `RestTemplate` WITH client cert (keystore + truststore) |
| `NoMtlsClientConfig.java` | Creates `RestTemplate` WITHOUT client cert (truststore only) |
| `DemoRunner.java` | Runs both scenarios on startup and prints results |

---

## How to Inspect and Debug

### View certificate contents

```bash
# See the server certificate in human-readable form
openssl x509 -in certs/server.crt -text -noout

# See the client certificate
openssl x509 -in certs/client.crt -text -noout

# See the CA certificate
openssl x509 -in certs/ca.crt -text -noout
```

### Verify the certificate chain

```bash
# Verify that server cert was signed by our CA
openssl verify -CAfile certs/ca.crt certs/server.crt
# Expected: certs/server.crt: OK

# Verify that client cert was signed by our CA
openssl verify -CAfile certs/ca.crt certs/client.crt
# Expected: certs/client.crt: OK
```

### Test with OpenSSL s_client

```bash
# Connect WITH client certificate (should succeed)
openssl s_client -connect localhost:8443 \
  -cert certs/client.crt \
  -key certs/client.key \
  -CAfile certs/ca.crt

# Connect WITHOUT client certificate (should fail with alert)
openssl s_client -connect localhost:8443 \
  -CAfile certs/ca.crt
```

### View keystore contents

```bash
# List what's inside the server keystore
keytool -list -keystore certs/server-keystore.p12 \
  -storetype PKCS12 -storepass changeit

# List what's in the server truststore
keytool -list -keystore certs/server-truststore.p12 \
  -storetype PKCS12 -storepass changeit
```

### Enable Java SSL debug logging

Add this JVM flag to see the full TLS handshake in the console:

```bash
# Full SSL debug output (very verbose — shows every handshake message)
java -Djavax.net.ssl.debug=all -jar target/mtls-server.jar

# Just handshake messages (less verbose)
java -Djavax.net.ssl.debug=ssl:handshake -jar target/mtls-server.jar
```

With debug enabled, you'll see output like:

```
javax.net.ssl|DEBUG|... ServerHello, TLSv1.3
javax.net.ssl|DEBUG|... CertificateRequest
javax.net.ssl|DEBUG|... Certificate message (from client)
javax.net.ssl|DEBUG|... CertificateVerify
```

### Test with curl

```bash
# With client certificate (should return JSON)
curl -v --cacert certs/ca.crt \
  --cert certs/client.crt \
  --key certs/client.key \
  https://localhost:8443/hello

# Without client certificate (should fail)
curl -v --cacert certs/ca.crt \
  https://localhost:8443/hello
# Expected error: SSL routines: certificate required
```
