# Security Considerations

## Authentication & Authorization

### API Security
- OAuth2 Resource Server with JWT validation
- Roles: `OPERATOR`, `REVIEWER`, `ADMIN`, `SYSTEM`
- Method-level security with `@PreAuthorize`

### Kafka Security
- SASL/SCRAM authentication for Kafka clients
- ACLs for topic authorization
- TLS encryption for data in transit

### Database Security
- PostgreSQL authentication with password
- Principle of least privilege for application user
- Connection pooling with HikariCP

## Data Protection

### Encryption at Rest
- Sensitive fields encrypted using JPA attribute converters
- Algorithm: AES-256-GCM
- Key management via environment variables

### Encryption in Transit
- TLS 1.3 for all external connections
- Kafka inter-broker communication encrypted
- Database connections over SSL (optional for local)

## Secrets Management

### Local Development
- Environment variables in docker-compose
- No secrets in code or configuration files

### Production
- HashiCorp Vault or AWS Secrets Manager
- Dynamic secret generation for database credentials
- Secret rotation policies

## Input Validation

### API Input
- Jakarta Validation annotations on DTOs
- JSON schema validation for complex payloads
- SQL injection prevention via parameterized queries
- XSS prevention via output encoding

### Event Input
- Schema validation against Avro schema
- Deserialization whitelist
- Message size limits

## Audit Logging

### Security Events
- Authentication success/failure
- Authorization failures
- Admin actions (connector registration, config changes)

### Business Events
- Order placed (who, when, amount)
- Payment processed (method, amount, status)
- Inventory reserved (sku, quantity)

## Rate Limiting

### API Rate Limits
- Per-user: 100 requests/minute
- Per-application: 1000 requests/minute
- Burst allowance: 20% above baseline

### Kafka Producer Limits
- Max request size: 1MB
- Max batch size: 16KB
- Retry backoff: exponential (100ms, 200ms, 400ms)

## Compliance

### Data Residency
- Database connection routing by region
- Kafka topic replication within region

### Retention
- Event retention: 30 days (configurable)
- Audit log retention: 7 years (compliance requirement)
- Backup strategy: daily snapshots

## Vulnerability Management

### Dependencies
- OWASP Dependency Check in CI
- Snyk for vulnerability scanning
- Regular dependency updates

### Container Security
- Minimal base images (Alpine)
- Non-root user in containers
- Image scanning with Trivy
- No secrets in Docker layers
