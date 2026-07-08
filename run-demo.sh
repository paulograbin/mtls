#!/bin/bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║              mTLS Demo - Full Run                            ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# -------------------------------------------------------------------
# Step 1: Generate certificates
# -------------------------------------------------------------------
echo "▶ Step 1: Generating certificates..."
echo ""
bash certs/generate-certs.sh
echo ""

# -------------------------------------------------------------------
# Step 2: Build applications
# -------------------------------------------------------------------
echo "▶ Step 2: Building server application..."
(cd server && mvn -q package -DskipTests)
echo "  Done."
echo ""

echo "▶ Step 3: Building client application..."
(cd client && mvn -q package -DskipTests)
echo "  Done."
echo ""

# REVIEW #5: Step numbering mismatch — comments say "Step 3" but echo says "Step 4"
# -------------------------------------------------------------------
# Step 3: Start the server
# -------------------------------------------------------------------
echo "▶ Step 4: Starting mTLS server on https://localhost:8443 ..."
cd server
java -jar target/mtls-server.jar &
SERVER_PID=$!
# REVIEW #2: No signal trap — server process orphaned on Ctrl+C. Add: trap 'kill $SERVER_PID 2>/dev/null; wait $SERVER_PID 2>/dev/null' EXIT INT TERM
cd "$PROJECT_DIR"

# Wait for server to be ready
echo "  Waiting for server to start..."
for i in $(seq 1 30); do
    if curl -sk https://localhost:8443/hello --cert certs/client.crt --key certs/client.key --cacert certs/ca.crt > /dev/null 2>&1; then
        echo "  Server is ready! (PID: $SERVER_PID)"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "  ERROR: Server did not start within 30 seconds"
        kill $SERVER_PID 2>/dev/null
        exit 1
    fi
    sleep 1
done
echo ""

# -------------------------------------------------------------------
# Step 4: Run the client
# -------------------------------------------------------------------
echo "▶ Step 5: Running client (demonstrates both scenarios)..."
echo ""
cd client
java -jar target/mtls-client.jar || true
cd "$PROJECT_DIR"

# -------------------------------------------------------------------
# Step 5: Cleanup
# -------------------------------------------------------------------
echo ""
echo "▶ Step 6: Stopping server..."
kill $SERVER_PID 2>/dev/null
wait $SERVER_PID 2>/dev/null
echo "  Server stopped."
echo ""
echo "Demo complete!"
