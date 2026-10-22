# Dove

Air-gapped, zero-network optical file sharing system for desktops and Android devices.

Dove transfers files bidirectionally between desktop computers and mobile devices with 100% optical transmission using animated QR code streams powered by rateless fountain erasure codes (Luby Transform).

---

## Installation

### Desktop Installation (Linux & macOS)

Install Dove using the automated installation command:

```bash
curl -fsSL https://raw.githubusercontent.com/berlin-566/dove/main/install.sh | bash
```

The installer will prompt you to choose an installation source:

```
==================================================
 Dove Installation Manager
==================================================
Select installation source:
  1) Latest Release (Recommended - precompiled standalone binary)
  2) Main Branch (Build from source via latest commit)

Enter choice [1-2] (default: 1):
```

- **Option 1 (Latest Release - Recommended):** Downloads the precompiled, standalone binary (`dove-linux-amd64`, `dove-darwin-arm64`, etc.) matching your operating system and CPU architecture.
- **Option 2 (Main Branch):** Fetches the latest source code from GitHub and compiles the binary locally using the Go toolchain.

*Note: Dove uses PHP CLI for session lifecycle and countdown coordination. If PHP is not currently installed, install it via your package manager (`sudo pacman -S php`, `sudo apt install php-cli`, or `brew install php`).*

### Android Installation (Mobile Phone)

Users can install the native Android application directly from GitHub Releases:

1. Navigate to [Dove Releases](https://github.com/berlin-566/dove/releases) on your Android phone.
2. Under **Assets**, tap **`dove.apk`** to download the standalone APK.
3. Open the downloaded file to install the application.

---

## Quick Start

### 1. Send File from PC to Phone (or Broadcast to 100 Phones)

Navigate to the directory containing your file and run:

```bash
dove send presentation.pdf --100m
```

- `--<N>m`: Optional session duration in minutes (e.g. `--100m`, `--15m`, `--5m`). If omitted, the session runs indefinitely until terminated with `Ctrl+C`.
- `--pin <pin>`: Optional 4-6 digit passcode for custom key derivation.
- `--terminal`: Streams animated QR frames directly to terminal stdout using ANSI half-block characters instead of opening a web browser.

**What happens:**
1. The Go engine compresses (`zstd`) and encrypts (`AES-256-GCM`) the file.
2. The rateless fountain encoder slices the payload into continuous symbols.
3. The local PHP session service (`web/session.php`) spawns a high-FPS animated QR player in your default desktop browser.
4. Up to 100 Android phones scan the screen simultaneously using the Dove mobile app to reconstruct and save the file.
5. When the time expires or `Ctrl+C` is pressed, the session securely wipes all temporary buffers and shuts down.

### 2. Send File from Phone to PC

To receive files from an Android phone to your desktop:

```bash
dove get
```

**What happens:**
1. Dove launches a local browser tab displaying a centered webcam optical scanner.
2. On your phone, open Dove, tap the **Send** tab, choose a file, and tap **Start Optical Stream**.
3. Point your phone screen at the desktop webcam.
4. Dove captures the fountain frames, verifies the integrity checksum, decrypts the payload, and saves the file directly into your terminal directory.

---

## Project Architecture

```
dove/
├── cli/                 # High-performance Go CLI engine
│   ├── cmd/             # 'dove send' and 'dove get' command implementations
│   ├── pkg/crypto/      # AES-256-GCM encryption & key derivation
│   ├── pkg/fountain/    # Luby Transform (LT) rateless fountain encoder/decoder
│   ├── pkg/qr/          # ANSI terminal and matrix QR generators
│   └── pkg/server/      # PHP built-in server supervisor
├── web/                 # PHP session coordinator & browser UI
│   ├── session.php      # Session lifecycle, countdown, self-destruction
│   ├── index.php        # Web dispatcher
│   ├── player.html      # High-FPS canvas QR streamer
│   ├── scanner.html     # Webcam optical scanner interface
│   └── assets/          # GitHub-style matte black CSS
├── android/             # Native Android application (Kotlin + Jetpack Compose)
│   ├── app/src/main/    # 3-tab UI (Send, Receive, Settings) & CameraX pipeline
│   └── build.gradle.kts # Android build configuration
├── install.sh           # Interactive desktop installation script
├── prd.md               # Product Requirements Document
├── agent.md             # AI Agent implementation rules & guidelines
└── .github/workflows/   # Multi-platform binary & APK release automation
```

---

## Documentation

- [Product Requirements Document (PRD)](./prd.md): Detailed specifications of frame binary schemas, rateless fountain distribution, and system flows.
- [Agent Implementation Guidelines](./agent.md): Mandatory constraints for contributors (anti-AI-slop rules, matte black `#010409` design system, SVG-only assets, and strict zero-emoji policy).

---

## License

MIT License. See [LICENSE](./LICENSE) for details.
