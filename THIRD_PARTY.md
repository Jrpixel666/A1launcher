# Third-party material

A1 Launcher's own code (everything not listed below) has no separate
license file in this repo yet; treat it as all-rights-reserved pending the
project owner choosing one.

## Omalauncher (MIT)

Several files are adapted from [Omalauncher](https://github.com/SimonSchubert/omalauncher)
by Simon Schubert and contributors, used under the MIT License reproduced
below. Where a file was trimmed or changed rather than copied verbatim,
its own header comment says so; none of it is a verbatim, unmodified copy
of the upstream file.

- `app/src/main/java/com/a1launcher/wayland/*.kt` — the Wayland protocol
  server (`Client.kt`, `Clipboard.kt`, `Compositor.kt`, `Input.kt`,
  `Keymap.kt`, `Keys.kt`, `Native.kt`, `Seat.kt`, `TextInput.kt`,
  `WaylandServer.kt`, `WindowRenderer.kt`, `Wire.kt`, `XdgShell.kt`,
  `WindowManager.kt`, `Streams.kt`) — package renamed
  `dev.omalauncher.wayland` → `com.a1launcher.wayland`; `WindowManager.kt`
  trimmed of the embedded-terminal-tile ("claim"/"release") path this
  project doesn't use.
- `app/src/main/cpp/wlbridge.c`, `app/src/main/cpp/CMakeLists.txt` — the
  native JNI bridge (unix sockets, shm, compositing into `ANativeWindow`);
  only the JNI symbol prefix changed, from
  `Java_dev_omalauncher_wayland_Native_` to
  `Java_com_a1launcher_wayland_Native_`.
- `scripts/fetch-proot.sh`, `patches/proot/0001-getresuid-32-bit-ids.patch` —
  builds `proot` from Termux's pinned source with the NDK. Changed to clone
  the pinned tag from `github.com/termux/proot` directly instead of
  downloading+verifying a GitHub codeload zip, because the zip endpoint
  wasn't reachable from the sandbox this was built in; swap back to the
  original curl+sha256 form in the file's comment if your environment can
  reach codeload.github.com.
- `app/src/main/java/com/a1launcher/linux/Proot.kt` — paths and the proot
  invocation itself; trimmed of Omalauncher's own data binds (its Omarchy
  theme/widget directories outside the rootfs) and of its Files-app path
  translation (`GuestFs`/`hostFile`/`guestFile`/`guestPath`), neither of
  which this project has an equivalent for yet.
- `app/src/main/java/com/a1launcher/linux/Bootstrap.kt` — the Arch Linux
  ARM download/extract/configure pipeline and the kernel-removal step;
  trimmed of Omalauncher's cross-app-update `StepJournal`/`Provisioner`
  tracking (replaced with simple on-disk existence checks) and of its
  Omarchy-specific steps (`cliamp`, its own themes).
- `app/src/main/java/com/a1launcher/linux/Agents.kt` — the `Agent` list and
  mise-based installer; trimmed of desktop-entry generation
  (`managed()`/`codexConfig()`/`Managed`/`Policy`) since this project
  doesn't have a Linux app drawer yet, and of `@StringRes` indirection
  (plain `String` descriptions instead, since this project doesn't have
  Omalauncher's string resources).
- `app/src/main/java/com/a1launcher/linux/ProcShim.kt` — ported essentially
  unchanged (package renamed only): stand-ins for the `/proc` files
  Android's SELinux policy hides from apps.
- `app/src/main/java/com/a1launcher/linux/Shell.kt`,
  `app/src/main/java/com/a1launcher/linux/Io.kt` — small shared utilities
  (shell quoting, job/output-tail tracking, atomic file writes, HTTP
  helpers); `shellQuote` reimplemented directly instead of delegating to
  Omalauncher's `owl` widget-language module, which this project doesn't
  include.
- `app/src/main/java/com/a1launcher/linux/LinuxRuntime.kt`,
  `app/src/main/java/com/a1launcher/linuxdesktop/LinuxWindowActivity.kt` —
  the process-wide Wayland-server/window-manager wiring and the
  one-Activity-per-window mechanism, adapted from Omalauncher's much larger
  `Runtime.kt` and `LinuxWindowActivity.kt` down to just this mechanism —
  trimmed of its themes, widgets, Files app, text editor, audio bridge,
  clipboard-to-Android sync, and image-paste/mic-dictation input chrome.
- The six previously-unidentified agent names (`agy`, `pi`, `omp`, `ori`,
  `muse`, `kimi`) in `linux/Agents.kt`'s `AGENTS` list were resolved by
  reading Omalauncher's own `THIRD_PARTY.md` (its agent-logo credits list),
  not copied from its `Agents.kt` source value-for-value by coincidence —
  though the resulting mise package specs for them are the same, since
  there's only one correct install spec per tool.

### License text (Omalauncher)

```
MIT License

Copyright (c) 2026 Simon Schubert and contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Termux `proot`

`scripts/fetch-proot.sh` builds `proot`, `libtalloc`, and
`libandroid-shmem` from [termux/proot](https://github.com/termux/proot)
and Termux's binary package pool at build time; none of their source or
binaries are checked into this repo. See that project for its own license.
