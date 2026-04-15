# app-bigconfig-website

Form handler backend for the bigconfig website. A Babashka HTTP server behind a Caddy reverse proxy, packaged for container deployment.

## Components

- `form.bb` — Babashka/http-kit server on port 8080. Accepts JSON POSTs, logs them, and responds with an echo. CORS is open to `*`.
- `Caddyfile` — Caddy listens on `:80` and reverse proxies to the Babashka server.
- `Procfile` — Hivemind runs `caddy` and `form` side by side.
- `Dockerfile` — Built on `caddy:2-alpine`, installs Babashka and Hivemind.
- `devenv.nix` — Dev shell with Clojure and Babashka.

## Run locally

```sh
bb form.bb
```

Then POST JSON to `http://localhost:8080/`.

## Run with Docker

```sh
docker build -t app-bigconfig-website .
docker run -p 80:80 app-bigconfig-website
```

## Dependencies

Declared in `bb.edn`:

- `com.draines/postal` — SMTP client (for future mail delivery).
