#!/bin/bash
# =====================================================
# UserInfo Capture - Linux (Kylin) Launcher
# Double-click this file or run: bash start-linux.sh
# =====================================================

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

# Detect architecture and find the right binary
ARCH=$(uname -m)
BIN=""

if [ "$ARCH" = "x86_64" ] || [ "$ARCH" = "amd64" ]; then
    BIN="$DIR/capture-proxy-linux-amd64"
elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
    BIN="$DIR/capture-proxy-linux-arm64"
fi

if [ -z "$BIN" ] || [ ! -f "$BIN" ]; then
    echo "=================================================="
    echo "Error: No matching binary for architecture: $ARCH"
    echo "Supported: x86_64 (amd64), aarch64 (arm64)"
    echo "=================================================="
    echo ""
    echo "Press Enter to exit..."
    read
    exit 1
fi

chmod +x "$BIN"

# Run the proxy
"$BIN"

# Close this terminal window after the proxy exits
exit 0
