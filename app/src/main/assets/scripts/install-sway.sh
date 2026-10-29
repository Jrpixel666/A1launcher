#!/data/data/com.a1launcher/files/usr/bin/sh
# install-sway.sh — pacman-installs Sway and a minimal, mobile-sane default
# setup inside the Arch guest. Sway was chosen over Hyprland/Qtile because it
# is Wayland (not X11 like Qtile), has no compositor animation overhead
# (unlike Hyprland), and is the one of the three most commonly run over
# proot/Termux with no GPU passthrough — it renders fine on llvmpipe/Zink
# software rendering, which is what we have available with no root.
#
# Env vars (set by ProotInstallService): PROOT_BIN, ROOTFS_DIR

set -eu
: "${PROOT_BIN:?}"
: "${ROOTFS_DIR:?}"

run() {
  "$PROOT_BIN" -0 -r "$ROOTFS_DIR" -b /dev -b /proc -b /sys -w /root "$@"
}

echo "STEP:InstallingCompositor"

run /usr/bin/pacman -S --noconfirm --needed \
  sway foot wmenu \
  mesa \
  polkit \
  dbus \
  xorg-xwayland \
  ttf-dejavu

mkdir -p "$ROOTFS_DIR/root/.config/sway"
cat > "$ROOTFS_DIR/root/.config/sway/config" <<'EOF'
# Minimal A1 Launcher default Sway config — tuned for a touch display with
# no hardware keyboard guaranteed, tiling kept simple on purpose.
set $term foot
set $menu wmenu-run

output * bg #0E0E12 solid_color
default_border pixel 2
gaps inner 4

bindsym Mod4+Return exec $term
bindsym Mod4+d exec $menu
bindsym Mod4+shift+q kill
bindsym Mod4+f fullscreen toggle
bindsym Mod4+shift+e exit

bar {
    position top
    status_command while true; do echo "A1 Linux Desktop"; sleep 60; done
}
EOF

echo "STEP:Done"
