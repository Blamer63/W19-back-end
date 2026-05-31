# Backend Deployment Guide

This backend is a Spring Boot 3 application that runs on Java 21, PostgreSQL, AWS S3/CloudFront, Google APIs, and the Python vision service.

## 1. Verify the build

Run tests locally when Java 21 is installed:

```bash
./mvnw test
```

Build the production container:

```bash
docker build -t locale-backend:latest .
```

The current Dockerfile builds the JAR in a Maven container and runs it with Eclipse Temurin Java 21.

## 2. Prepare production configuration

Set the Spring production profile:

```bash
SPRING_PROFILES_ACTIVE=prod
```

Required environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://<db-host>:5432/<db-name>
SPRING_DATASOURCE_USERNAME=<db-user>
SPRING_DATASOURCE_PASSWORD=<db-password>
JWT_SECRET=<long-random-secret>
ALLOWED_ORIGINS=https://<frontend-domain>
GOOGLE_PLACES_KEY=<google-places-key>
GOOGLE_TRANSLATE_API_KEY=<google-translate-key>
AWS_REGION=ap-southeast-2
AWS_S3_BUCKET_CUSTOMER=<bucket-name>
AWS_CLOUDFRONT_DOMAIN=<cloudfront-domain>
VISION_API_URL=http://<vision-service-host>:8000
VISION_TIMEOUT_MS=60000
VISION_SUPPORTED_LANGUAGES=en,es,fr,ja
WEB_PUSH_ENABLED=true
WEB_PUSH_VAPID_PUBLIC_KEY=<vapid-public-key>
WEB_PUSH_VAPID_PRIVATE_KEY=<vapid-private-key>
WEB_PUSH_VAPID_SUBJECT=mailto:<admin-or-support-email>
WEB_PUSH_TTL_SECONDS=2419200
```

Provide AWS credentials through the hosting platform secret store, an IAM role, or the standard AWS environment variables. Do not bake secrets into the Docker image.

## 3. Prepare the database

Production uses:

```yaml
spring.jpa.hibernate.ddl-auto: validate
spring.sql.init.mode: never
```

That means the application will not create or mutate production tables. Before deployment, create the production schema manually or add a migration tool such as Flyway or Liquibase. A fresh database will not boot successfully until its schema matches the JPA entities.

For the current repo, the local seed scripts are development-only. Do not run `data.sql` automatically in production.

If browser Web Push is enabled, apply `docs/sql/create_push_subscriptions_table.sql` before booting with the production profile. Keep the VAPID key pair stable after users subscribe; replacing it invalidates existing browser subscriptions.

## 4. Deploy the services

Deploy these runtime services:

- PostgreSQL
- Spring Boot backend image
- Vision service image from `ml-services/vision-service`
- Frontend separately, configured to call the backend URL

Backend health check:

```bash
GET /actuator/health
```

Vision service health check:

```bash
GET /health
```

## Temporary phone demo with HTTPS

For a free phone demo, run the full local Docker stack and expose only the frontend container through Cloudflare Quick Tunnel:

```powershell
.\scripts\start-phone-tunnel.ps1 -Build
```

The script prints a temporary `https://...trycloudflare.com` URL. Open that URL on your phone to test mobile browser behavior, PWA install, location, and camera flows over HTTPS.

If the containers are already up to date, omit `-Build`:

```powershell
.\scripts\start-phone-tunnel.ps1
```

Stop the public tunnel when finished:

```powershell
.\scripts\start-phone-tunnel.ps1 -Stop
```

This is for development and demos only. Use a real domain and managed HTTPS for production.

## 5. Smoke test after deployment

After the container starts, verify:

```bash
curl https://<backend-domain>/actuator/health
curl -X POST https://<backend-domain>/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"<test-user-email>","password":"<test-password>"}'
```

Then test a file upload and scanner request if those features are enabled, because they depend on S3, CloudFront, and the vision service.

## Deployment readiness checklist

- Tests pass with Java 21.
- Docker image builds cleanly.
- `.env` is not committed or copied into images.
- `SPRING_PROFILES_ACTIVE=prod` is set.
- `ALLOWED_ORIGINS` is set to the real frontend domain, not `*`.
- Production DB schema exists before startup.
- Secrets are stored in the hosting platform secret manager.
- S3 bucket, CloudFront domain, and IAM permissions are configured.
- Vision service is deployed and reachable from the backend.
- Web Push VAPID keys are configured if `WEB_PUSH_ENABLED=true`.
- `/actuator/health` passes after deployment.
