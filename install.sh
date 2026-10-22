#!/usr/bin/env bash
# Dove Installer: Air-gapped optical file sharing system
# Installs standalone binary and web runtime assets to user environment

set -euo pipefail

REPO="berlin-566/dove"
INSTALL_BIN_DIR="${HOME}/.local/bin"
INSTALL_SHARE_DIR="${HOME}/.local/share/dove"
WEB_DEST="${INSTALL_SHARE_DIR}/web"

detect_platform() {
  OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
  ARCH="$(uname -m)"

  case "${OS}" in
    linux) OS="linux" ;;
    darwin) OS="darwin" ;;
    *) echo "Unsupported operating system: ${OS}"; exit 1 ;;
  esac

  case "${ARCH}" in
    x86_64|amd64) ARCH="amd64" ;;
    aarch64|arm64) ARCH="arm64" ;;
    *) echo "Unsupported architecture: ${ARCH}"; exit 1 ;;
  esac

  TARGET_BINARY="dove-${OS}-${ARCH}"
}

prompt_choice() {
  echo "=================================================="
  echo " Dove Installation Manager"
  echo "=================================================="
  echo "Select installation source:"
  echo "  1) Latest Release (Recommended - precompiled standalone binary)"
  echo "  2) Main Branch (Build from source via latest commit)"
  echo ""
  
  # Read user choice from /dev/tty if stdin is piped via curl | bash
  if [ -t 0 ]; then
    read -r -p "Enter choice [1-2] (default: 1): " USER_CHOICE
  elif [ -r /dev/tty ]; then
    read -r -p "Enter choice [1-2] (default: 1): " USER_CHOICE </dev/tty
  else
    USER_CHOICE="1"
  fi

  USER_CHOICE="${USER_CHOICE:-1}"
}

install_from_release() {
  echo ""
  echo "Fetching latest release metadata from GitHub..."
  mkdir -p "${INSTALL_BIN_DIR}" "${INSTALL_SHARE_DIR}"

  LATEST_JSON=$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" 2>/dev/null || true)
  
  DOWNLOAD_URL=""
  if [ -n "${LATEST_JSON}" ] && echo "${LATEST_JSON}" | grep -q "browser_download_url"; then
    DOWNLOAD_URL=$(echo "${LATEST_JSON}" | grep "browser_download_url" | grep "${TARGET_BINARY}" | head -n 1 | cut -d '"' -f 4 || true)
  fi

  if [ -n "${DOWNLOAD_URL}" ]; then
    echo "Downloading precompiled binary: ${TARGET_BINARY}..."
    curl -fsSL "${DOWNLOAD_URL}" -o "${INSTALL_BIN_DIR}/dove"
    chmod +x "${INSTALL_BIN_DIR}/dove"
  else
    echo "Release binary not yet published for ${TARGET_BINARY}. Falling back to source build..."
    install_from_source
    return
  fi

  echo "Downloading web session assets..."
  mkdir -p "${WEB_DEST}"
  curl -fsSL "https://raw.githubusercontent.com/${REPO}/main/web/session.php" -o "${WEB_DEST}/session.php"
  curl -fsSL "https://raw.githubusercontent.com/${REPO}/main/web/index.php" -o "${WEB_DEST}/index.php"
  curl -fsSL "https://raw.githubusercontent.com/${REPO}/main/web/player.html" -o "${WEB_DEST}/player.html"
  curl -fsSL "https://raw.githubusercontent.com/${REPO}/main/web/scanner.html" -o "${WEB_DEST}/scanner.html"
  mkdir -p "${WEB_DEST}/assets"
  curl -fsSL "https://raw.githubusercontent.com/${REPO}/main/web/assets/style.css" -o "${WEB_DEST}/assets/style.css"
  curl -fsSL "https://raw.githubusercontent.com/${REPO}/main/web/assets/qrcode.min.js" -o "${WEB_DEST}/assets/qrcode.min.js"
  curl -fsSL "https://raw.githubusercontent.com/${REPO}/main/web/assets/jsQR.min.js" -o "${WEB_DEST}/assets/jsQR.min.js"
}

install_from_source() {
  echo ""
  echo "Building Dove from main branch source..."
  mkdir -p "${INSTALL_BIN_DIR}" "${INSTALL_SHARE_DIR}"

  TEMP_DIR=$(mktemp -d)
  trap 'rm -rf "${TEMP_DIR}"' EXIT

  echo "Fetching source archive..."
  curl -fsSL "https://github.com/${REPO}/archive/refs/heads/main.tar.gz" | tar -xz -C "${TEMP_DIR}"

  SRC_DIR="${TEMP_DIR}/dove-main"

  # Verify Go installation
  if ! command -v go >/dev/null 2>&1; then
    if [ -x "${HOME}/.local/go/bin/go" ]; then
      export PATH="${HOME}/.local/go/bin:${PATH}"
    else
      echo "Go compiler not found. Please install Go (https://go.dev) or choose Release binary option."
      exit 1
    fi
  fi

  echo "Compiling dove CLI..."
  (
    cd "${SRC_DIR}/cli"
    go build -o "${INSTALL_BIN_DIR}/dove" main.go
  )
  chmod +x "${INSTALL_BIN_DIR}/dove"

  echo "Installing web session runtime..."
  rm -rf "${WEB_DEST}"
  cp -r "${SRC_DIR}/web" "${WEB_DEST}"
}

verify_environment() {
  echo ""
  echo "Checking environment dependencies..."

  if ! command -v php >/dev/null 2>&1; then
    echo "Notice: PHP executable is not currently in PATH."
    echo "  Dove uses PHP for session lifecycle and countdown management."
    echo "  Install PHP via your system package manager:"
    echo "    - Arch Linux: sudo pacman -S php"
    echo "    - Ubuntu/Debian: sudo apt update && sudo apt install php-cli"
    echo "    - macOS: brew install php"
  else
    echo "PHP is available: $(php -v | head -n 1)"
  fi

  case ":${PATH}:" in
    *:"${INSTALL_BIN_DIR}":*) ;;
    *)
      echo ""
      echo "Notice: ${INSTALL_BIN_DIR} is not in your current PATH."
      echo "Add the following line to your ~/.bashrc or ~/.zshrc:"
      echo "  export PATH=\"${INSTALL_BIN_DIR}:\$PATH\""
      ;;
  esac
}

main() {
  detect_platform
  prompt_choice

  if [ "${USER_CHOICE}" = "2" ]; then
    install_from_source
  else
    install_from_release
  fi

  verify_environment

  echo ""
  echo "=================================================="
  echo " Dove successfully installed to ${INSTALL_BIN_DIR}/dove"
  echo "=================================================="
  echo "Usage:"
  echo "  dove send <filename> [--100m]   Broadcast file via animated QR code"
  echo "  dove get                       Receive file via camera scanner"
  echo "  dove help                      Show complete command documentation"
  echo ""
}

main "$@"
