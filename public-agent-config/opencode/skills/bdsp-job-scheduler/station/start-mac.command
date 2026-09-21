#!/bin/bash
# =====================================================
# UserInfo Capture - macOS Launcher
# Double-click this file to start the capture proxy
# =====================================================

# Get the directory where this script lives
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

# Find the Mac binary (supports both arm64 and amd64)
BIN=""
if [ -f "$DIR/capture-proxy-mac" ]; then
    BIN="$DIR/capture-proxy-mac"
elif [ -f "$DIR/capture-proxy-darwin-arm64" ]; then
    BIN="$DIR/capture-proxy-darwin-arm64"
elif [ -f "$DIR/capture-proxy-darwin-amd64" ]; then
    BIN="$DIR/capture-proxy-darwin-amd64"
fi

if [ -z "$BIN" ]; then
    echo "=================================================="
    echo "Error: Could not find the capture proxy binary."
    echo "Please make sure capture-proxy-mac is in the same folder."
    echo "=================================================="
    echo ""
    echo "Press Enter to close..."
    read
    exit 1
fi

# Make sure it's executable
chmod +x "$BIN"

# Run the proxy
"$BIN"

# Close this terminal window after the proxy exits
osascript -e 'tell application "Terminal" to close front window' >/dev/null 2>&1 &
exit 0
