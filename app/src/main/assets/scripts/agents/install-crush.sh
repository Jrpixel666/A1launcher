#!/data/data/com.a1launcher/files/usr/bin/sh
# Installs Crush (Charm's multi-model terminal agent) via its official
# install script. If charm.sh changes this URL, Charm also publishes Crush
# through most system package managers (apt/yum/brew/pacman via their repo) —
# check https://github.com/charmbracelet/crush for the current method.
set -eu
: "${PROOT_BIN:?}"
: "${ROOTFS_DIR:?}"

run() { "$PROOT_BIN" -0 -r "$ROOTFS_DIR" -b /dev -b /proc -b /sys -w /root "$@"; }

echo "STEP:InstallingAgent:crush"
run /usr/bin/pacman -S --noconfirm --needed curl
run /bin/sh -c 'curl -fsSL https://charm.sh/crush/install | sh'
echo "STEP:Done"
