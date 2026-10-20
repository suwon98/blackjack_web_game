FROM eclipse-temurin:17-jdk AS backend-build
ARG TARGETARCH
WORKDIR /build
COPY blackjack/ .
RUN --mount=type=cache,id=blackjack-gradle-${TARGETARCH},target=/root/.gradle,sharing=locked sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre AS backend
WORKDIR /app
COPY --from=backend-build /build/build/libs/*-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar","--spring.profiles.active=prod"]

FROM node:22-alpine AS frontend-build
WORKDIR /build
COPY frontend/frontend-blackjack/package*.json ./
RUN npm ci
COPY frontend/frontend-blackjack/ .
RUN npm run build

FROM caddy:2-alpine AS web
COPY --from=frontend-build /build/dist /srv
COPY deploy/Caddyfile /etc/caddy/Caddyfile
