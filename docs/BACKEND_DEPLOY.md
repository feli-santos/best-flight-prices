# Backend deployment

The server is a stateless container (Dockerfile): listens on $PORT, configured by environment variables, JSON logs to stdout, /v1/health. Host undecided (ADR 0009); candidates: Google Cloud Run (needs billing account), Fly.io/Render (small VM). Cloudflare Workers is not suitable (JS/Wasm only, 10 ms CPU on free). Test locally with podman via make lab-deploy.

Behind Cloud Run or Fly set `TRUST_PROXY=true`, otherwise every user shares one rate-limit bucket. Keep the provider client from following redirects (already configured) so the access token is never forwarded to another host.
