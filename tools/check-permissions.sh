#!/usr/bin/env bash
# Fails if an APK asks for any permission beyond the ones Grumpy QR is supposed
# to have. Run it on every release build (see docs/PUBLISHING.md), so a
# dependency can never quietly add network access (or anything else).
#
# Usage: tools/check-permissions.sh path/to/app.apk
set -euo pipefail

APK="$1"
AAPT2="$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)aapt2"

# CAMERA is the only permission users ever see. The second one is an internal,
# signature-level permission AndroidX uses to protect the app's own broadcasts.
ALLOWED='^(android\.permission\.CAMERA|io\.github\.nimbice\.grumpyqr(\.debug)?\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)$'

PERMISSIONS="$("$AAPT2" dump permissions "$APK" | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p")"
echo "Permissions requested by $APK:"
echo "$PERMISSIONS" | sed 's/^/  /'

UNEXPECTED="$(echo "$PERMISSIONS" | grep -Ev "$ALLOWED" || true)"
if [ -n "$UNEXPECTED" ]; then
  echo "::error::Unexpected permissions in $APK:"
  echo "$UNEXPECTED"
  exit 1
fi
echo "OK: only the expected permissions."
