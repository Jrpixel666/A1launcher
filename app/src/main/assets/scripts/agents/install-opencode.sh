#!/data/data/com.a1launcher/files/usr/bin/sh
# Installs opencode (SST's open-source terminal coding agent) via its
# official install script.
set -eu
: "${PROOT_BIN:?}"
: "${ROOTFS_DIR:?}"

run() { "$PROOT_BIN" -0 -r "$ROOTFS_DIR" -b /dev -b /proc -b /sys -w /root "$@"; }

echo "STEP:InstallingAgent:opencode"
run /usr/bin/pacman -S --noconfirm --needed curl unzip
run /bin/sh -c 'curl -fsSL https://opencode.ai/install | bash'
echo "STEP:Done"
