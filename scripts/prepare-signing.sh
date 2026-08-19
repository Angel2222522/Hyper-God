#!/usr/bin/env bash
set -euo pipefail

if [ -z "${KEYSTORE_BASE64:-}" ] || [ -z "${KEYSTORE_PASSWORD:-}" ] || [ -z "${KEY_ALIAS:-}" ] || [ -z "${KEY_PASSWORD:-}" ] || [ -z "${EXPECTED_CERT_SHA256:-}" ]; then
  echo "Permanent Hyper God signing secret is missing. Refusing release."
  exit 1
fi

SIGNING_DIR="${RUNNER_TEMP:-/tmp}/hyper-god-signing"
mkdir -p "$SIGNING_DIR"
KEYSTORE_PATH="$SIGNING_DIR/hyper-god-release.jks"
printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$KEYSTORE_PATH"

EXPECTED_SHA256=$(printf '%s' "$EXPECTED_CERT_SHA256" | tr -d ':' | tr '[:lower:]' '[:upper:]')
ACTUAL_SHA256=$(keytool -list -v \
  -keystore "$KEYSTORE_PATH" \
  -storepass "$KEYSTORE_PASSWORD" \
  -alias "$KEY_ALIAS" \
  | sed -n 's/^[[:space:]]*SHA256: //p' \
  | head -n 1 \
  | tr -d ':' \
  | tr '[:lower:]' '[:upper:]')

if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
  echo "Signing certificate does not match the permanent Hyper God certificate. Refusing release."
  exit 1
fi

echo "HYPER_GOD_KEYSTORE_PATH=$KEYSTORE_PATH" >> "$GITHUB_ENV"
echo "HYPER_GOD_KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD" >> "$GITHUB_ENV"
echo "HYPER_GOD_KEY_ALIAS=$KEY_ALIAS" >> "$GITHUB_ENV"
echo "HYPER_GOD_KEY_PASSWORD=$KEY_PASSWORD" >> "$GITHUB_ENV"
