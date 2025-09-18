#!/usr/bin/env bash
set -euo pipefail

# Generate self-signed PEM key/cert for HTTPS E2E tests.
# Output: adapter-http-tests/src/test/resources/tls/test-key.pem and test-cert.pem

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TLS_DIR="$ROOT_DIR/adapter-http-tests/src/test/resources/tls"
mkdir -p "$TLS_DIR"

KEY_FILE="$TLS_DIR/test-key.pem"
CERT_FILE="$TLS_DIR/test-cert.pem"

if ! command -v openssl >/dev/null 2>&1; then
  echo "[ERROR] openssl not found. Please install openssl and re-run." >&2
  exit 1
fi

echo "Generating self-signed certificate in $TLS_DIR ..."
openssl req -x509 -newkey rsa:2048 \
  -keyout "$KEY_FILE" \
  -out "$CERT_FILE" \
  -nodes -days 365 \
  -subj "/C=NA/ST=NA/L=NA/O=OFKit/OU=Tests/CN=localhost"

echo "Generated:"
ls -l "$KEY_FILE" "$CERT_FILE"

