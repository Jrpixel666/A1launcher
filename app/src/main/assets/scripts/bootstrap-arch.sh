#!/data/data/com.a1launcher/files/usr/bin/sh
# bootstrap-arch.sh — fetch and unpack Arch Linux ARM into the app's private
# storage, entirely under proot, with no root and no Termux dependency.
#
# Invoked by ProotInstallService with these env vars already set:
#   PROOT_BIN    path to the bundled statically-linked `proot` executable
#   ROOTFS_DIR   e.g. /data/data/com.a1launcher/files/arch
#   CACHE_DIR    scratch space for the downloaded rootfs tarball
#   ARCH_ABI     "aarch64" or "armv7" — matches Build.SUPPORTED_ABIS
#
# This mirrors what termux's proot-distro does, minus the Termux dependency:
# download the official Arch Linux ARM rootfs, extract it, patch resolv.conf,
# and drop in an A1-specific pacman.conf that blocks kernel packages (see
# pacman-no-kernel.conf — proot shares the host kernel, so linux/linux-* and
# kernel-module packages can never do anything useful here, and we disable
# them outright rather than let them fail or confuse the user later).

set -eu

: "${PROOT_BIN:?PROOT_BIN not set}"
: "${ROOTFS_DIR:?ROOTFS_DIR not set}"
: "${CACHE_DIR:?CACHE_DIR not set}"
: "${ARCH_ABI:?ARCH_ABI not set}"

case "$ARCH_ABI" in
  aarch64) ROOTFS_URL="http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz" ;;
  armv7)   ROOTFS_URL="http://os.archlinuxarm.org/os/ArchLinuxARM-armv7-latest.tar.gz" ;;
  *) echo "Unsupported ABI for Arch Linux ARM: $ARCH_ABI" >&2; exit 1 ;;
esac

mkdir -p "$ROOTFS_DIR" "$CACHE_DIR"
TARBALL="$CACHE_DIR/archlinux-rootfs.tar.gz"

echo "STEP:DownloadingRootfs"
if [ ! -f "$TARBALL" ]; then
  curl -L --fail --progress-bar -o "$TARBALL.part" "$ROOTFS_URL"
  mv "$TARBALL.part" "$TARBALL"
fi

echo "STEP:ExtractingRootfs"
tar -xpzf "$TARBALL" -C "$ROOTFS_DIR" --numeric-owner

echo "STEP:ConfiguringPacman"
echo "nameserver 1.1.1.1" > "$ROOTFS_DIR/etc/resolv.conf"
echo "nameserver 8.8.8.8" >> "$ROOTFS_DIR/etc/resolv.conf"

cp "$(dirname "$0")/pacman-no-kernel.conf" "$ROOTFS_DIR/etc/pacman.conf.a1-kernel-block"
if ! grep -q "a1-kernel-block" "$ROOTFS_DIR/etc/pacman.conf" 2>/dev/null; then
  cat "$ROOTFS_DIR/etc/pacman.conf.a1-kernel-block" >> "$ROOTFS_DIR/etc/pacman.conf"
fi

"$PROOT_BIN" \
  -0 -r "$ROOTFS_DIR" \
  -b /dev -b /proc -b /sys \
  -w /root \
  /usr/bin/pacman-key --init

"$PROOT_BIN" \
  -0 -r "$ROOTFS_DIR" \
  -b /dev -b /proc -b /sys \
  -w /root \
  /usr/bin/pacman-key --populate archlinuxarm

"$PROOT_BIN" \
  -0 -r "$ROOTFS_DIR" \
  -b /dev -b /proc -b /sys \
  -w /root \
  /usr/bin/pacman -Syu --noconfirm

echo "STEP:Done"
