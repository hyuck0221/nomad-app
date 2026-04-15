#!/usr/bin/env bash
# Dev helper — push the already-downloaded Gemma 4 E2B .task file directly to
# the app's external files dir via adb, skipping the first-launch in-app
# download. Useful during development when you don't want to redownload 1.9 GB
# every time you wipe app data.
#
# Usage:
#   scripts/push-model.sh                  # default device
#   scripts/push-model.sh -s <serial>      # specific device
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MODEL_NAME="gemma-4-E2B-it.litertlm"
LOCAL_FILE="$PROJECT_ROOT/model/$MODEL_NAME"
PKG="com.nomad.travel"
REMOTE_DIR="/sdcard/Android/data/$PKG/files/models"

if [[ ! -f "$LOCAL_FILE" ]]; then
  echo "❌ $LOCAL_FILE not found."
  echo "   Run: curl -L -o \"$LOCAL_FILE\" \\"
  echo "        \"https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_NAME\""
  exit 1
fi

ADB_ARGS=("$@")
adb "${ADB_ARGS[@]}" shell "mkdir -p $REMOTE_DIR"
echo "→ Pushing $(du -h "$LOCAL_FILE" | cut -f1) to $REMOTE_DIR/"
adb "${ADB_ARGS[@]}" push "$LOCAL_FILE" "$REMOTE_DIR/$MODEL_NAME"
echo "✓ Done. Launch the app — the setup card should be gone."
