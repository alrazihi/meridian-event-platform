#!/bin/bash
# PostgreSQL SSL Certificate Generation Script
# Run this script to generate self-signed certificates for development
# For production, use certificates from a trusted CA

set -e

SSL_DIR="$(dirname "$0")/ssl"
mkdir -p "$SSL_DIR"

echo "Generating CA certificate..."
openssl req -new -x509 -days 365 -nodes \
    -out "$SSL_DIR/ca.crt" \
    -keyout "$SSL_DIR/ca.key" \
    -subj "/CN=Meridian CA"

echo "Generating server certificate..."
openssl req -new -nodes \
    -out "$SSL_DIR/server.csr" \
    -keyout "$SSL_DIR/server.key" \
    -subj "/CN=postgres"

openssl x509 -req -in "$SSL_DIR/server.csr" \
    -CA "$SSL_DIR/ca.crt" -CAkey "$SSL_DIR/ca.key" -CAcreateserial \
    -out "$SSL_DIR/server.crt" -days 365 -sha256

echo "Generating client certificate..."
openssl req -new -nodes \
    -out "$SSL_DIR/client.csr" \
    -keyout "$SSL_DIR/client.key" \
    -subj "/CN=meridian-client"

openssl x509 -req -in "$SSL_DIR/client.csr" \
    -CA "$SSL_DIR/ca.crt" -CAkey "$SSL_DIR/ca.key" -CAcreateserial \
    -out "$SSL_DIR/client.crt" -days 365 -sha256

# Set proper permissions
chmod 600 "$SSL_DIR/ca.key" "$SSL_DIR/server.key" "$SSL_DIR/client.key"
chmod 644 "$SSL_DIR/ca.crt" "$SSL_DIR/server.crt" "$SSL_DIR/client.crt"

# Copy to expected locations
cp "$SSL_DIR/server.crt" "$SSL_DIR/server.crt"
cp "$SSL_DIR/server.key" "$SSL_DIR/server.key"
cp "$SSL_DIR/ca.crt" "$SSL_DIR/ca.crt"

echo "SSL certificates generated in $SSL_DIR"
echo "CA: $SSL_DIR/ca.crt"
echo "Server: $SSL_DIR/server.crt / $SSL_DIR/server.key"
echo "Client: $SSL_DIR/client.crt / $SSL_DIR/client.key"
echo ""
echo "For production, replace with certificates from a trusted CA"