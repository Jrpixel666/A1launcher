#!/data/data/com.a1launcher/files/usr/bin/sh
# OpenRouter isn't a CLI — it's a hosted-model API. This script drops an env
# file any proot-side tool (or A1's own in-app assistant, see
# AgentBackend.kt) can source to use OpenRouter as its model provider.
#
# API_KEY is passed by SetupViewModel from the (encrypted, on-device-only)
# key the user entered in the setup wizard; it is never written to the APK
# or any log.
set -eu
: "${PROOT_BIN:?}"
: "${ROOTFS_DIR:?}"
: "${API_KEY:?}"

echo "STEP:InstallingAgent:openrouter"

mkdir -p "$ROOTFS_DIR/root/.config/a1launcher"
cat > "$ROOTFS_DIR/root/.config/a1launcher/openrouter.env" <<EOF
export OPENROUTER_API_KEY="$API_KEY"
export OPENAI_BASE_URL="https://openrouter.ai/api/v1"
EOF
chmod 600 "$ROOTFS_DIR/root/.config/a1launcher/openrouter.env"

echo "STEP:Done"
