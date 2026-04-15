# Stage 1: Builder
FROM caddy:2-alpine AS builder

ARG TARGETARCH
WORKDIR /app

# Install build dependencies
RUN apk add --no-cache bash curl

# Install Babashka
RUN curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install \
    && chmod +x install \
    && ./install \
    && rm install

# Download and extract Hivemind
RUN curl -sL "https://github.com/DarthSim/hivemind/releases/download/v1.1.0/hivemind-v1.1.0-linux-${TARGETARCH}.gz" \
    | gunzip > /usr/local/bin/hivemind \
    && chmod +x /usr/local/bin/hivemind

# Stage 2: Final Image
FROM caddy:2-alpine

RUN apk add libc6-compat

# Copy binaries from the builder stage
COPY --from=builder /usr/local/bin/bb /usr/local/bin/bb
COPY --from=builder /usr/local/bin/hivemind /usr/local/bin/hivemind

WORKDIR /srv

# Combine COPY commands to reduce layers
COPY Caddyfile form.bb Procfile ./

EXPOSE 80

CMD ["hivemind", "/srv/Procfile"]
