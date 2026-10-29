#!/data/data/com.a1launcher/files/usr/bin/sh
# Installs the Grok CLI (xAI) via npm.
#
# NOTE: the npm package name for third-party Grok CLIs has moved before.
# If this fails, check what `npm search grok-cli` / xAI's current docs say
# and update the package name below — everything else in this script
# (node/npm bootstrap, invocation shape) stays the same.
set -eu
: "${PROOT_BIN:?}"
: "${ROOTFS_DIR:?}"

run() { "$PROOT_BIN" -0 -r "$ROOTFS_DIR" -b /dev -b /proc -b /sys -w /root "$@"; }

echo "STEP:InstallingAgent:grok"
run /usr/bin/pacman -S --noconfirm --needed nodejs npm
run /usr/bin/npm install -g grok-cli
echo "STEP:Done"
