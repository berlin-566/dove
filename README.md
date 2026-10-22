# Dove

Air-gapped, zero-network optical file sharing system for desktops and Android devices.

Dove transfers files bidirectionally between desktop computers and mobile devices with 100% optical transmission using animated QR code streams powered by rateless fountain erasure codes (Luby Transform).

## Key Features

- **Zero-Network Architecture:** No Wi-Fi, no Bluetooth, no cellular, and no cloud servers. Fully air-gapped file transfers via light photons from screen to camera.
- **1-to-Many Broadcast:** Transmit files simultaneously to 100+ Android devices from a single desktop screen without synchronization bottlenecks.
- **End-to-End Cryptography:** Automatic zstd compression and AES-256-GCM encryption for all optical payloads.
- **Lightweight Desktop Core:** High-performance Go binary backed by a lightweight PHP session coordinator (`web/session.php`).
- **Minimalist Android Client:** Native Kotlin and Jetpack Compose mobile app built with GitHub Mobile aesthetic (matte black theme, crisp SVG icons, zero emojis).

## Project Documentation

- [Product Requirements Document (PRD)](./prd.md): Comprehensive system architecture, optical protocol schemas, CLI specifications, and Android app requirements.
- [Agent Implementation Guidelines](./agent.md): Mandatory engineering constraints, anti-AI-slop rules, zero-emoji policy, and design specifications.

## Repository Layout

- `cli/`: Go command-line tool (`dove send`, `dove get`).
- `web/`: PHP session manager (`session.php`) and browser UI.
- `android/`: Native Android application (Kotlin + Jetpack Compose).
- `docs/`: Supplementary architectural documentation.

## License

MIT License. See [LICENSE](./LICENSE) for details.
