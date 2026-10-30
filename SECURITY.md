# Security

- The fare-provider token exists only as a server environment variable. It must never appear in the apps, the repo, logs, or URLs sent to clients.
- `.env` is gitignored; `make secret-scan` (gitleaks) runs in CI and before pushes.
- The public API is rate limited per IP, validated, and budget-capped. Attestation (App Attest / Play Integrity) is a future ADR.
- Report vulnerabilities privately via GitHub Security Advisories on this repository.
- If a secret leaks: revoke it at the provider immediately, then rewrite nothing — rotate.
