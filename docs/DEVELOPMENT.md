# Development

Prereqs: JDK 17+ (JDK 26 present), Gradle wrapper, Android command-line SDK, Xcode 27 (iOS), XcodeGen, Maestro, gitleaks, podman. `make lab-setup` installs and verifies what it can; `make lab-doctor` reports gaps.
Config: copy .env.example to .env (gitignored). `TRAVELPAYOUTS_TOKEN` switches fares from sample to real; `TRAVELPAYOUTS_MARKER` (optional) is appended to booking links as `?marker=`. Versions pinned in gradle/libs.versions.toml; confirm compatibility (Kotlin, Compose MP, AGP, Gradle) before bumping.
Conventional commits. Tests first.
