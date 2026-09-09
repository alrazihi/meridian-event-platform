# Secrets Management

## Overview

The Meridian Event Platform requires the following secrets to operate in production:

| Secret | Purpose | Required |
|--------|---------|----------|
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password | Yes |
| `SPRING_KAFKA_SASL_JAAS_CONFIG` | Kafka SASL authentication | Yes |
| `JWT_PUBLIC_KEY` | RSA public key for JWT verification | Yes |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | Alternative to JWT_PUBLIC_KEY | Optional |

## Kubernetes Secrets

### Create Secret
```bash
kubectl create secret generic meridian-event-platform-secrets \
  --namespace=meridian \
  --from-literal=SPRING_DATASOURCE_PASSWORD='<db-password>' \
  --from-literal=SPRING_KAFKA_SASL_JAAS_CONFIG='org.apache.kafka.common.security.plain.PlainLoginModule required username="meridian" password="<kafka-password>";' \
  --from-literal=JWT_PUBLIC_KEY='<base64-encoded-rsa-public-key>'
```

### Verify Secret
```bash
kubectl get secret meridian-event-platform-secrets -n meridian -o yaml
```

## Environment Variables

All secrets should be provided via environment variables. Never commit secrets to version control.

### Required Environment Variables

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://meridian-postgres:5432/meridian?sslmode=require
SPRING_DATASOURCE_USERNAME=meridian
SPRING_DATASOURCE_PASSWORD=<from-secret>

# Kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=meridian-kafka:9092
SPRING_KAFKA_SECURITY_PROTOCOL=SASL_SSL
SPRING_KAFKA_SASL_MECHANISM=PLAIN
SPRING_KAFKA_SASL_JAAS_CONFIG=<from-secret>

# JWT
JWT_PUBLIC_KEY=<from-secret>
# Alternative: JWK Set URI
# SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=https://auth.example.com/.well-known/jwks.json
```

## JWT Key Rotation

1. Generate new RSA keypair:
   ```bash
   openssl genpkey -algorithm RSA -out new-private.pem -pkeyopt rsa_keygen_bits:2048
   openssl rsa -pubout -in new-private.pem -out new-public.pem
   ```

2. Update Kubernetes secret with new public key:
   ```bash
   kubectl create secret generic meridian-event-platform-secrets \
     --namespace=meridian \
     --from-literal=JWT_PUBLIC_KEY="$(cat new-public.pem)" \
     --dry-run=client -o yaml | kubectl apply -f -
   ```

3. Rolling restart:
   ```bash
   kubectl rollout restart deployment/meridian-event-platform -n meridian
   ```

4. Verify all pods use new key:
   ```bash
   kubectl get pods -n meridian -l app=meridian-event-platform
   ```

## Dev/Test Mode

In `dev` and `test` profiles, the application auto-generates a test RSA keypair. **Never use these profiles in production.**

To verify active profile:
```bash
curl http://localhost:8080/actuator/info | grep activeProfiles
```
