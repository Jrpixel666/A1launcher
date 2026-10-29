#!/data/data/com.a1launcher/files/usr/bin/sh
# Installs cursor-agent (Cursor's terminal coding agent) via its official
# install script.
set -eu
: "${PROOT_BIN:?}"
: "${ROOTFS_DIR:?}"

run() { "$PROOT_BIN" -0 -r "$ROOTFS_DIR" -b /dev -b /proc -b /sys -w /root "$@"; }

echo "STEP:InstallingAgent:cursor-agent"
run /usr/bin/pacman -S --noconfirm --needed curl
run /bin/sh -c 'curl https://cursor.com/install -fsS | bash'
echo "STEP:Done"
