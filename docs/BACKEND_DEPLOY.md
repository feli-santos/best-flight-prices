# Backend deployment

The server is a stateless container (Dockerfile): listens on $PORT, configured by environment variables, JSON logs to stdout, /v1/health. Host undecided (ADR 0009); candidates: Google Cloud Run (needs billing account), Fly.io/Render (small VM). Cloudflare Workers is not suitable (JS/Wasm only, 10 ms CPU on free). Test locally with podman via make lab-deploy.
