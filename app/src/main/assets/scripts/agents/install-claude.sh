#!/data/data/com.a1launcher/files/usr/bin/sh
# Installs Claude Code (Anthropic's agentic coding CLI) via npm, inside the
# Arch guest. Requires nodejs/npm, installed first if missing.
set -eu
: "${PROOT_BIN:?}"
: "${ROOTFS_DIR:?}"

run() { "$PROOT_BIN" -0 -r "$ROOTFS_DIR" -b /dev -b /proc -b /sys -w /root "$@"; }

echo "STEP:InstallingAgent:claude"
run /usr/bin/pacman -S --noconfirm --needed nodejs npm
run /usr/bin/npm install -g @anthropic-ai/claude-code
echo "STEP:Done"
