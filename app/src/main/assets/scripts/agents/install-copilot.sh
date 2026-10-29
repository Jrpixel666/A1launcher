#!/data/data/com.a1launcher/files/usr/bin/sh
# Installs the GitHub Copilot CLI as a `gh` extension — this is GitHub's own
# documented distribution path (gh extension install github/gh-copilot),
# rather than a standalone binary.
set -eu
: "${PROOT_BIN:?}"
: "${ROOTFS_DIR:?}"

run() { "$PROOT_BIN" -0 -r "$ROOTFS_DIR" -b /dev -b /proc -b /sys -w /root "$@"; }

echo "STEP:InstallingAgent:copilot"
run /usr/bin/pacman -S --noconfirm --needed github-cli
run /usr/bin/gh extension install github/gh-copilot
echo "STEP:Done"
