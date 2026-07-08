#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

PASSWORD="changeit"
DAYS=365

echo "============================================"
echo "  mTLS Certificate Generation"
echo "============================================"
echo ""

# Clean previous artifacts
rm -f *.key *.crt *.csr *.p12 *.srl

# -------------------------------------------------------------------
# 1. Create Certificate Authority (CA)
# -------------------------------------------------------------------
echo "[1/7] Generating CA private key and self-signed certificate..."
openssl req -x509 -newkey rsa:2048 \
  -keyout ca.key -out ca.crt \
  -days $DAYS -nodes \
  -subj "/CN=Demo-CA/O=mTLS-Demo"

echo "       CA Subject: CN=Demo-CA, O=mTLS-Demo"
echo ""

# -------------------------------------------------------------------
# 2. Generate Server certificate
# -------------------------------------------------------------------
echo "[2/7] Generating server private key and CSR..."
openssl req -newkey rsa:2048 \
  -keyout server.key -out server.csr \
  -nodes \
  -subj "/CN=localhost/O=mTLS-Demo"

echo "[3/7] Signing server certificate with CA (SAN: localhost, 127.0.0.1)..."
openssl x509 -req -in server.csr \
  -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out server.crt -days $DAYS \
  -extfile <(printf "subjectAltName=DNS:localhost,IP:127.0.0.1")

echo "       Server Subject: CN=localhost, O=mTLS-Demo"
echo ""

# -------------------------------------------------------------------
# 3. Generate Client certificate
# -------------------------------------------------------------------
echo "[4/7] Generating client private key and CSR..."
openssl req -newkey rsa:2048 \
  -keyout client.key -out client.csr \
  -nodes \
  -subj "/CN=demo-client/O=mTLS-Demo"

echo "[5/7] Signing client certificate with CA..."
openssl x509 -req -in client.csr \
  -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out client.crt -days $DAYS

echo "       Client Subject: CN=demo-client, O=mTLS-Demo"
echo ""

# -------------------------------------------------------------------
# 4. Create PKCS12 Keystores
# -------------------------------------------------------------------
echo "[6/7] Creating PKCS12 keystores..."

# Server keystore: contains server's private key + certificate
openssl pkcs12 -export \
  -in server.crt -inkey server.key \
  -out server-keystore.p12 \
  -name server \
  -password pass:$PASSWORD

echo "       server-keystore.p12  (server private key + cert)"

# Client keystore: contains client's private key + certificate
openssl pkcs12 -export \
  -in client.crt -inkey client.key \
  -out client-keystore.p12 \
  -name client \
  -password pass:$PASSWORD

echo "       client-keystore.p12  (client private key + cert)"
echo ""

# -------------------------------------------------------------------
# 5. Create Truststores (contain the CA cert)
# -------------------------------------------------------------------
echo "[7/7] Creating PKCS12 truststores..."

# Server truststore: CA cert to verify incoming client certificates
keytool -import -trustcacerts \
  -alias ca \
  -file ca.crt \
  -keystore server-truststore.p12 \
  -storetype PKCS12 \
  -storepass $PASSWORD \
  -noprompt

echo "       server-truststore.p12 (CA cert - verifies client certs)"

# Client truststore: CA cert to verify the server's certificate
keytool -import -trustcacerts \
  -alias ca \
  -file ca.crt \
  -keystore client-truststore.p12 \
  -storetype PKCS12 \
  -storepass $PASSWORD \
  -noprompt

echo "       client-truststore.p12 (CA cert - verifies server cert)"
echo ""

# -------------------------------------------------------------------
# Summary
# -------------------------------------------------------------------
echo "============================================"
echo "  Certificate generation complete!"
echo "============================================"
echo ""
echo "  Files generated:"
echo "    CA:      ca.key, ca.crt"
echo "    Server:  server.key, server.crt, server-keystore.p12, server-truststore.p12"
echo "    Client:  client.key, client.crt, client-keystore.p12, client-truststore.p12"
echo ""
echo "  All keystore/truststore password: $PASSWORD"
echo ""
