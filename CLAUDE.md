# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

Form-handler backend for the bigconfig website. An HTTP server accepts JSON POSTs from the marketing site, emails the payload to the owner via Resend SMTP, and echoes the body back. Caddy fronts the server as a reverse proxy; Hivemind supervises both processes inside a single container.

## Runtime layout

Two processes run side-by-side under `Procfile` (Hivemind):

- `caddy` listens on `:80` and reverse-proxies to `localhost:8080` (see `Caddyfile`, which disables `auto_https` because TLS is terminated upstream).
- `form` is the Clojure handler. Routing is done via `reitit.ring`: `POST /form/:form-name` JSON-parses the body, sends an email through `postal` using `resend-config` (subject `"IMPORTANT: <form-name> form submitted"`), and responds `{:status "success" :you-sent …}`. All other routes/methods fall through to a default handler that returns `200 "UP"`. `wrap-cors` opens CORS to `*` and handles preflight.

SMTP credentials come from the process environment. `read-system-env` keywordises env var names (`SMTP_ADDRESS` → `:smtp-address`, etc.), so the handler expects: `SMTP_ADDRESS`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_PORT`, `MAILER_FROM_ADDRESS`, `TARGET_EMAIL` (recipient). `env` and `resend-config` are `delay`s, so values are read on first use rather than at namespace load.

## Commands

Local dev:

```sh
clj -M -m io.github.amiorin.app-bigconfig-website.core
```

This starts http-kit on `:8080`. POST JSON to it directly, or run Caddy alongside via `hivemind Procfile` to exercise the `:80 → :8080` path.

Build the uberjar (tools.build via the `:build` alias, entry point in `build.clj`):

```sh
clj -T:build uber    # produces target/app-bigconfig-website-0.1.0-standalone.jar
clj -T:build clean
```

The uberjar requires AOT — `core.clj` carries `(:gen-class)` and `build.clj` compiles only that namespace. If you rename or split the main ns, update `main` in `build.clj`.

Container build/run (three-stage Dockerfile: `clojure:temurin-25-tools-deps-alpine` builds the jar, an `alpine:3` stage pulls Hivemind, the final `caddy:2-alpine` image adds `openjdk25` and copies `/srv/app.jar`):

```sh
docker build -t app-bigconfig-website .
docker run -p 80:80 --env-file .env app-bigconfig-website
```

`Procfile` runs `java -jar app.jar` for the `form` process, so the jar is renamed on copy — the version suffix in `build.clj` doesn't leak into the container path.

Deployment (packaging via the `once` tool pinned in `bb.edn`):

```sh
bb package create
bb package delete
```

`bb.edn` only defines the `package` task — it wires `io.github.amiorin.once.package/once*` with an inline workflow that provisions an OCI `VM.Standard.A1.Flex` instance, Cloudflare DNS for `bigconfig.website`, S3-backed Terraform state in `tf-state-251213589273-eu-west-1`, and Resend SMTP. Changes to infra go here, not in separate Terraform files. Babashka is still required on the dev host for this task even though the runtime no longer uses it.

## Clojure tooling

- `deps.edn` pins runtime deps (`http-kit`, `postal`, `cheshire`, `metosin/reitit`) and the `:build` alias (`io.github.clojure/tools.build`). A `:test` alias wires cognitect's `test-runner` against `test/`.
- `.clj-kondo/` and `.lsp/` are checked in; clj-kondo already has imports for http-kit, encore, fs, and rewrite-clj — prefer updating those over re-importing.
- `.nrepl-port` is written when an nREPL is running. The rich-comment block at the bottom of `core.clj` starts/stops the http-kit server in-REPL; a separate `send-email` invocation is the canonical smoke test for SMTP wiring.
- `API.org` holds `restclient`-style request snippets against `https://forms.bigconfig.website/form/…` — handy for manual end-to-end checks.
