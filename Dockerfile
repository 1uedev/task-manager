# Demonstration image of the task manager (TASK-031, DEC-071): the backend serves the built web interface.
# Configuration comes only from environment variables; the image contains no password, no key and no data.
# Base images are pinned by tag and digest so that a rebuild uses the same tool versions as CI.

FROM node:24.22.0-bookworm-slim@sha256:91acba91b842bec0bf7ea6f5e09c644c7ccad72c4996ed9616d861d1c7cd60d4 AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e AS backend
# The Maven wrapper verifies the checksum of the zip distribution and needs unzip for it; the base image has none.
RUN apt-get update \
 && apt-get install --yes --no-install-recommends unzip \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /build/backend
COPY backend/mvnw ./
COPY backend/.mvn/ .mvn/
COPY backend/pom.xml ./
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline
COPY backend/src/ src/
# The interface becomes part of the application archive and is served from the same origin as the API.
COPY --from=frontend /build/frontend/dist/ src/main/resources/static/
# Tests run in CI against real PostgreSQL; the image build only packages the tested sources.
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package \
 && cp target/task-manager-backend-0.0.1-SNAPSHOT.jar /build/app.jar

FROM eclipse-temurin:25.0.4.1_1-jre-noble@sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5
RUN groupadd --system --gid 10001 taskmanager \
 && useradd --system --uid 10001 --gid taskmanager --home-dir /app --shell /usr/sbin/nologin taskmanager
WORKDIR /app
# The file store of the attachments (TASK-051). Mount a volume here; an empty named volume takes over this owner.
RUN mkdir -p /data/files && chown 10001:10001 /data/files
COPY --from=backend --chown=root:root --chmod=0444 /build/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/readiness HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q UP <&3"]
# Without arguments the web server starts. Commands such as bootstrap-admin, reset-admin-password and
# rebuild-search are passed as arguments and end after they ran. The heap follows the container memory limit.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
