# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS api-contract
WORKDIR /workspace/backend
COPY backend/gradlew backend/gradlew
COPY backend/gradle backend/gradle
COPY backend/build.gradle backend/settings.gradle backend/gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies
COPY backend/src backend/src
RUN ./gradlew --no-daemon openapi3

FROM node:22.19.0-bookworm-slim AS frontend-build
WORKDIR /workspace
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY frontend/package.json frontend/package.json
RUN corepack enable && corepack prepare pnpm@10.33.0 --activate && pnpm install --frozen-lockfile
COPY scripts scripts
COPY frontend frontend
COPY --from=api-contract /workspace/backend/build/api-spec/openapi3.yaml backend/build/api-spec/openapi3.yaml
RUN pnpm api:generate && test -s frontend/src/api/generated.ts && pnpm frontend:build && test -f frontend/dist/index.html

FROM eclipse-temurin:25-jdk AS application-build
WORKDIR /workspace/backend
COPY backend/gradlew backend/gradlew
COPY backend/gradle backend/gradle
COPY backend/build.gradle backend/settings.gradle backend/gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies
COPY backend/src backend/src
COPY --from=api-contract /workspace/backend/build/api-spec/openapi3.yaml build/api-spec/openapi3.yaml
COPY --from=frontend-build /workspace/frontend/dist src/main/resources/static
COPY --from=frontend-build /workspace/backend/build/api-docs src/main/resources/static/assets/api-docs
RUN test -f src/main/resources/static/index.html && ./gradlew --no-daemon clean bootJar && test -f build/libs/*.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 aierp \
    && useradd --system --uid 10001 --gid aierp --home-dir /app --shell /usr/sbin/nologin aierp
COPY --from=application-build /workspace/backend/build/libs/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
