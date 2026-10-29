#!/data/data/com.a1launcher/files/usr/bin/sh
# Installs the OpenAI Codex CLI via npm.
set -eu
: "${PROOT_BIN:?}"
: "${ROOTFS_DIR:?}"

run() { "$PROOT_BIN" -0 -r "$ROOTFS_DIR" -b /dev -b /proc -b /sys -w /root "$@"; }

echo "STEP:InstallingAgent:codex"
run /usr/bin/pacman -S --noconfirm --needed nodejs npm
run /usr/bin/npm install -g @openai/codex
echo "STEP:Done"
