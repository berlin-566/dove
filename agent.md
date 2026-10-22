# AGENT GUIDELINES & SPECIFICATIONS: DOVE

This document defines mandatory, non-negotiable standards and constraints for any AI agent, contributor, or developer writing code, documentation, or designing UI for the **Dove** repository.

---

## 1. Absolute Constraints (Non-Negotiable)

### 1.1 Zero Emoji Policy
- **Absolute Prohibition:** You MUST NOT use emojis anywhere in this project.
  - No emojis in UI strings, buttons, dialogs, tooltips, toasts, or notifications.
  - No emojis in terminal CLI logs, stdout/stderr, progress bars, or help text.
  - No emojis in source code comments, variable names, or docstrings.
  - No emojis in Git commit messages or Pull Request titles/descriptions.
  - No emojis in Markdown documentation or architecture diagrams.
- **Enforcement:** Any commit containing emoji characters (Unicode range `\p{Extended_Pictographic}`) must fail linting and be rejected immediately.

### 1.2 Anti-AI Slop Mandate
- **No Gimmicky Visuals:** Do NOT introduce generic "AI generated" UI patterns:
  - No colorful pulsating glowing neon borders.
  - No floating glassmorphism blur cards with multicolored drop shadows.
  - No bouncing bubbly buttons or gratuitous micro-animations.
  - No fake marketing fluff text or bloated template placeholders.
- **Industrial, Utilitarian Aesthetic:** Design interfaces that look like developer tools built by senior engineers at GitHub or JetBrains: crisp borders, high contrast, clean typography, dense information layout, and lightning-fast responsiveness.

### 1.3 Iconography & Assets
- **SVG Only:** All icons must be pure, clean SVG vector drawables (Android Vector Drawables or inline `<svg>` elements).
- **Style:** Standard 16px / 24px stroke-based icons inspired by GitHub Octicons or standard Google Material Symbols (Sharp/Outlined).
- **Prohibitions:** No rasterized PNG icons, no icon fonts with bundled emoji fallbacks, and no emoji glyphs masquerading as buttons.

---

## 2. UI / UX Design Specifications

### 2.1 Aesthetic Benchmark: GitHub Android Mobile App
The Android application and the desktop web companion must strictly follow the aesthetic and structural conventions of the official **GitHub Android app**.

### 2.2 Color Palette

#### Dark Mode (Default: True Matte Black)
- **Canvas / Background:** `#010409` (Deepest Matte Black)
- **Surface / Card Background:** `#0D1117`
- **Secondary Surface:** `#161B22`
- **Borders & Dividers:** `#30363D` (1dp crisp borders)
- **Primary Text:** `#C9D1D9`
- **Secondary / Muted Text:** `#8B949E`
- **Accent / Interactive:** `#58A6FF` (Subtle GitHub Blue) or `#238636` (Action Green)

#### Light Mode
- **Canvas / Background:** `#FFFFFF`
- **Surface / Card Background:** `#F6F8FA`
- **Secondary Surface:** `#EAEEF2`
- **Borders & Dividers:** `#D0D7DE`
- **Primary Text:** `#24292F`
- **Secondary / Muted Text:** `#57606A`
- **Accent / Interactive:** `#0969DA`

### 2.3 Component Rules
- **Corner Radii:** Use strict 6dp or 8dp corner radius for cards and buttons. Never use pill-shaped bubbly radii unless explicitly defining badges.
- **Card Styling:** Solid matte background with a 1dp outline border (`#30363D` in dark mode). No blurred backdrop filters or drop-shadow elevation bloat.
- **Font Stack:** Clean system sans-serif (`Inter`, `Roboto`, or system default) with monospace for file hashes, chunk numbers, and code metrics.

---

## 3. Technology Stack & Coding Standards

### 3.1 Go CLI (`/cli`)
- **Language Level:** Go 1.22+
- **Architecture:** Cobra/Viper pattern or clean standard library CLI flags. Keep dependencies minimal.
- **Core Principles:**
  - Fast startup time (< 20ms).
  - Single static binary distribution.
  - Zero heap allocation bottlenecks in the fountain coding loop.
  - All errors must be handled explicitly; avoid unhandled panics.
  - Output clean ANSI text with clean spinners/progress bars; no emoji indicators.

### 3.2 PHP Session Service (`/web`)
- **Language Level:** PHP 8.2+
- **Architecture:** Clean, readable, native procedural or basic OOP PHP.
- **Prohibitions:**
  - DO NOT install heavy enterprise frameworks (Laravel, Symfony fullstack).
  - Keep logic self-contained in `session.php` and modular companion files.
- **Session Lifecycle Standards:**
  - Validate all inputs strictly.
  - Enforce timeout duration requested by `--<N>m` flag.
  - Once session timer elapses or termination signal is received, execute complete shredding/cleanup of temporary files on disk.
  - Zero external database dependencies; utilize local SQLite or in-memory array/ephemeral files for session state tracking.

### 3.3 Android Application (`/android`)
- **Language & Framework:** Kotlin 1.9+ with Jetpack Compose.
- **Navigation Structure:** Strict 3-tab layout:
  1. `Send`: File selection, optical chunk generation, high-FPS animated QR display.
  2. `Receive`: Full-screen CameraX optical scanner with bounding reticle and fountain progress bar.
  3. `Settings`: Matte Black / Light mode toggle, transfer history list, optical FPS settings.
- **Camera Integration:** CameraX ImageAnalysis pipeline. Must process frames asynchronously off the UI thread without dropping camera preview frames.
- **Storage:** Use Android Storage Access Framework (SAF) to write received files to public `Downloads/` directory securely.

---

## 4. Optical Fountain Protocol Integrity

- **Erasure Code Algorithm:** Must adhere strictly to rateless Luby Transform (LT) codes or UR-style fountain codes.
- **Interoperability:** Ensure chunk slicing, degree calculation, PRNG seed generation, and XOR reconstruction logic match 1:1 between the Go CLI encoder/decoder, PHP session streamer, and Kotlin mobile decoder/encoder.
- **Validation:** Every transmitted block must include CRC32 validation before being added to the fountain decoding graph.

---

## 5. Repository & Git Workflow

- **Commit Format:** Conventional Commits:
  - `feat: implement rateless fountain encoder in Go`
  - `fix: prevent camera buffer overrun on low-end Android devices`
  - `docs: update optical protocol specification in PRD`
  - `refactor: optimize session destruction logic in session.php`
- **Zero Emoji in Commits:** No `git commit -m ":sparkles: ..."` or emoji prefixes.
- **Code Reviews:** Any review generated by AI agents must verify conformance with:
  1. Zero emojis.
  2. No AI slop / Matte Black GitHub styling compliance.
  3. Air-gapped optical integrity (zero unexpected network calls).
