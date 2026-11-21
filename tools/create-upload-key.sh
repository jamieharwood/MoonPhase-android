#!/usr/bin/env bash
# Creates the Google Play upload key and keystore.properties (both git-ignored).
# Run once from the repo root. Back up the .jks file and its password somewhere safe:
# if you lose them you must ask Google Play support to reset the upload key.
set -euo pipefail
cd "$(dirname "$0")/.."

KEYSTORE="${1:-$HOME/keys/moonphase-upload.jks}"
ALIAS="upload"

if [[ -e "$KEYSTORE" ]]; then
  echo "$KEYSTORE already exists; not overwriting it." >&2
  exit 1
fi
mkdir -p "$(dirname "$KEYSTORE")"

KEYTOOL="keytool"
if ! command -v keytool >/dev/null 2>&1; then
  KEYTOOL="$(ls -d "$HOME"/.jdks/*/bin/keytool 2>/dev/null | head -1)"
  [[ -x "$KEYTOOL" ]] || { echo "keytool not found: install a JDK (e.g. openjdk-21-jdk-headless)." >&2; exit 1; }
fi

read -rsp "Choose a keystore password (min 6 characters): " PASS; echo
read -rsp "Repeat it: " PASS2; echo
[[ "$PASS" == "$PASS2" ]] || { echo "Passwords don't match." >&2; exit 1; }
[[ ${#PASS} -ge 6 ]] || { echo "Password too short." >&2; exit 1; }
read -rp "Your name or organisation for the certificate (e.g. Jane Smith): " CN

"$KEYTOOL" -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=$CN"

umask 077
cat > keystore.properties <<PROPS
storeFile=$KEYSTORE
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
PROPS
chmod 600 "$KEYSTORE" keystore.properties
echo
echo "Created $KEYSTORE and keystore.properties."
echo "Back up the .jks file and the password now (password manager + offline copy)."
