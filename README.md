# app-bigconfig-website

Form handler backend for the bigconfig website. A Clojure/http-kit server behind a Caddy reverse proxy, packaged as a JVM uberjar for container deployment.

## Components

- `src/io/github/amiorin/app_bigconfig_website/core.clj` — http-kit server on port 8080. Accepts JSON POSTs, emails the payload via Resend SMTP, and responds with an echo. CORS is open to `*`.
- `build.clj` — `tools.build` entry point; produces the uberjar via the `:build` alias in `deps.edn`.
- `Caddyfile` — Caddy listens on `:80` and reverse proxies to the server on `:8080`.
- `Procfile` — Hivemind runs `caddy` and `form` (`java -jar app.jar`) side by side.
- `Dockerfile` — Multi-stage build: `clojure:temurin-25-tools-deps-alpine` builds the uberjar, `alpine:3` fetches Hivemind, final image is `caddy:2-alpine` with `openjdk25`.
- `devenv.nix` — Dev shell with Clojure and Babashka.

## Run locally

```sh
clj -M -m io.github.amiorin.app-bigconfig-website.core
```

Then POST JSON to `http://localhost:8080/`. Requires `SMTP_ADDRESS`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_PORT`, and `MAILER_FROM_ADDRESS` in the environment.

## Build the uberjar

```sh
clj -T:build uber
```

Output: `target/app-bigconfig-website-0.1.0-standalone.jar`.

## Run with Docker

```sh
docker build -t app-bigconfig-website .
docker run -p 80:80 --env-file .env app-bigconfig-website
```

## Deploy

```sh
bb package create
```

## Dependencies

Runtime deps are declared in `deps.edn`:

- `http-kit/http-kit` — HTTP server
- `cheshire/cheshire` — JSON
- `com.draines/postal` — SMTP client (Resend)
