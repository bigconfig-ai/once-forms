# Stage 1: Build uberjar
FROM clojure:temurin-25-tools-deps-alpine AS builder
WORKDIR /app
COPY deps.edn build.clj ./
COPY src ./src
RUN clojure -T:build uber

# Stage 2: Fetch Hivemind
FROM alpine:3 AS hivemind
ARG TARGETARCH
RUN apk add --no-cache curl
RUN curl -sL "https://github.com/DarthSim/hivemind/releases/download/v1.1.0/hivemind-v1.1.0-linux-${TARGETARCH}.gz" \
    | gunzip > /usr/local/bin/hivemind \
    && chmod +x /usr/local/bin/hivemind

# Stage 3: Final Image
FROM caddy:2-alpine

RUN apk add --no-cache openjdk25 tini

COPY --from=builder /app/target/once-forms-0.1.0-standalone.jar /srv/app.jar
COPY --from=hivemind /usr/local/bin/hivemind /usr/local/bin/hivemind

WORKDIR /srv

COPY Caddyfile Procfile ./

EXPOSE 80

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["hivemind", "Procfile"]
