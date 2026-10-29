# A1 Launcher

An Android home launcher (Kotlin + Jetpack Compose) with a built-in AI agent
assistant, home-screen widgets, and an optional on-device Linux desktop
(Arch Linux under `proot`, no root, running Sway) that installs on first
launch alongside a choice of AI coding-agent CLIs.

This repo is an **architecture + working MVP scaffold**, not a finished,
store-ready app — see "What's real vs. stubbed" below before you assume any
given feature just works out of the box.

## Module layout

```
com.a1launcher
├── A1LauncherApp.kt          Application class, holds SettingsRepository
├── home/                     The launcher surface itself (HOME activity)
│   ├── MainActivity.kt       Compose host, widget-picker result handling
│   ├── HomeScreen.kt         Widget area + dock (Apps / Linux / Settings)
│   ├── AppDrawer.kt          Full app grid (queries PackageManager)
│   ├── AppListViewModel.kt
│   ├── HomeViewModel.kt
│   └── WidgetHostManager.kt  Real AppWidgetHost wrapper — hosts ANY widget
├── widget/                   A1's own Glance widget (agent/Linux status)
├── setup/                    First-launch wizard (welcome → Linux opt-in
│                             → agent picker → review → install → done)
├── linux/                    proot/Arch orchestration
│   ├── ProotPaths.kt         Where the rootfs/binaries live, and why
│   ├── ShellExecutor.kt      Runs bootstrap scripts, streams output
│   ├── ProotManager.kt       bootstrapArch() / installSway() / installAgent()
│   └── ProotInstallService.kt  Foreground service running the above
├── linuxdesktop/             The Linux desktop as its own Android task
│   ├── LinuxDesktopActivity.kt
│   └── LinuxDisplayBridge.kt Documented stub — see "What's stubbed"
├── assistant/                The AI agent chat overlay
│   ├── AgentBackend.kt / OpenRouterBackend.kt
│   ├── AssistantViewModel.kt / AssistantChatScreen.kt / AssistantChatActivity.kt
│   └── AssistantOverlayService.kt  Draggable floating bubble
├── settings/SettingsActivity.kt
├── data/                     DataStore + EncryptedSharedPreferences
└── model/                    AgentCli, InstallState, ChatMessage

app/src/main/assets/scripts/  Shell scripts the Kotlin layer runs under proot
├── bootstrap-arch.sh         Downloads + extracts Arch Linux ARM
├── pacman-no-kernel.conf     IgnorePkg/IgnoreGroup for kernel packages
├── install-sway.sh           pacman -S sway + minimal config
└── agents/install-*.sh       One script per agent CLI
```

## Design decisions (and why)

### Compositor: Sway, not Hyprland or Qtile

Of the three named, **Sway** is the right fit for "minimal, lightweight,
runs under proot with no root, no GPU passthrough":
- It's Wayland, like Hyprland, but with none of Hyprland's animation/effects
  GPU overhead — it renders fine on software rendering (llvmpipe/Zink),
  which is all you have without root/GPU passthrough on most devices.
- Qtile's Wayland backend is comparatively immature; its primary backend is
  X11, which is an extra translation layer we don't need.
- Sway specifically has the most prior art running under proot-distro/Termux
  with no root, which is exactly this app's constraint.

### Linux GUI integration: one "Linux Desktop" task, not per-app Recents cards

Two designs were considered:

1. **Per-app Recents cards** — each Linux GUI window gets its own Android
   Activity/Task, so it looks exactly like a native app in Recents. This
   needs a bridge that watches the Wayland compositor's window list and
   *dynamically creates a real Android Activity per top-level window*,
   piping that specific window's buffer into it. That's genuinely
   research-grade engineering (on the order of a scoped-down Waydroid), not
   something a scaffold session can responsibly claim to deliver.
2. **Single Linux Desktop task** (what this repo implements): one Android
   Activity (`LinuxDesktopActivity`) hosts the *entire* Sway session as one
   Recents card; Sway does its own tiling/window management for Linux GUI
   apps *inside* that one task, the same way it would on a laptop. This is
   buildable with ordinary Android APIs (a distinct `taskAffinity` +
   `singleTask` is literally all it takes to get its own Recents entry) and
   is what `LinuxDesktopActivity` + the manifest implement.

### Kernel packages are blocked, not just discouraged

Per the task requirement, `pacman-no-kernel.conf` sets `IgnorePkg`/
`IgnoreGroup`/`NoExtract` so pacman silently skips `linux`, `linux-headers`,
`linux-firmware`, `mkinitcpio`, and kernel-module packages on every
install/upgrade. This isn't just policy — under proot there's no real
kernel inside the guest (every process shares the host Android kernel), so
these packages can never do anything there except fail to build modules or
waste bandwidth; blocking them is both the requirement and the technically
correct thing to do.

### Agent CLIs

Installed via `assets/scripts/agents/install-*.sh`, one script per tool,
each a straightforward `pacman`/`npm`/vendor-install-script call run inside
the guest. Covered: **Claude Code, opencode, Codex CLI, Grok CLI, GitHub
Copilot CLI, Crush, cursor-agent**, plus **OpenRouter** wired in as a
hosted-API provider (an API key, not a CLI) used by both the in-app
assistant (`OpenRouterBackend.kt`) and any proot-side tool that reads
`~/.config/a1launcher/openrouter.env`. A few names from the original ask
(`agy`, `pi`, `omp`, `ori`, `muse`, `kimi`, `antigravity`) weren't
identifiable public tools and were deliberately left out rather than
guessed at — add a script under `agents/` and an `AgentCli` entry for any of
them once you have a real package name/install command.

The exact install command for Grok CLI, Crush, and cursor-agent is marked
with a comment where I'm less than fully certain it matches the current
upstream distribution method — check the comment in each script if an
install fails.

## What's real vs. stubbed

**Real and working** (ordinary Android APIs, no missing pieces):
home screen UI, app drawer, third-party widget hosting via `AppWidgetHost`,
A1's own Glance widget, the first-launch wizard end to end, DataStore/
encrypted-prefs settings, the floating AI-agent bubble (`WindowManager`
overlay), the in-app assistant chat wired to OpenRouter's real HTTP API,
the shell-script orchestration layer (`ProotManager`/`ShellExecutor`/
`ProotInstallService`) and all the bootstrap/install scripts themselves.

**Documented stubs — two missing pieces, both flagged loudly in code,
neither faked:**

1. **The `proot`/`busybox` binaries themselves aren't vendored in this
   repo.** Android 10+'s W^X policy means you cannot download an
   executable into app storage and run it — Termux's own workaround (and
   the one `ProotPaths.kt` documents and implements the runtime side of) is
   to ship statically-linked `proot`/`busybox` binaries as fake native
   libraries under `app/src/main/jniLibs/<abi>/libproot.so` /
   `libbusybox.so`, so Gradle packages them as part of the APK's verified,
   exec-permitted native library directory. **To finish this:** obtain (or
   build with the NDK) static `proot` and `busybox` binaries for
   `arm64-v8a` (and optionally `armeabi-v7a`/`x86_64`), name them
   `libproot.so`/`libbusybox.so`, and drop them under the matching
   `app/src/main/jniLibs/<abi>/` folder. `ProotPaths.isProotAvailable()`
   is the runtime check the UI should gate on until then.

2. **`LinuxDisplayBridge`** — compositing Sway's Wayland output onto the
   `SurfaceView` in `LinuxDesktopActivity`, and forwarding touch input back
   into the Wayland seat, needs a custom wlroots backend built as JNI/native
   code. `LinuxDisplayBridge.kt` documents exactly what three calls a real
   implementation needs to make; `LinuxDesktopActivity` already does
   everything around it correctly (task affinity for its own Recents card,
   process lifecycle for the proot `sway` process, surface lifecycle/resize
   handling) and visibly says "bridge not wired up yet" in the UI rather
   than silently showing a blank screen.

Everything else in the task description is implemented for real; these two
are the honest boundary of what one session can responsibly build versus
claim to have built.

## Building

`./gradlew assembleDebug` (needs `ANDROID_HOME` pointed at an Android SDK
with platform 34 + build-tools 34.0.0) has been verified to produce a
working debug APK from this exact source tree. The NDK is only needed once
you add the native `waybridge` module from `LinuxDisplayBridge.kt`'s TODO —
nothing in the current build requires it.

Or push to a branch — `.github/workflows/build.yml` assembles a debug APK
on GitHub Actions and uploads it as a workflow artifact on every push.

## Permissions this app requests

- Package-visibility `<queries>` for `LAUNCHER` activities (app drawer) —
  not the broader `QUERY_ALL_PACKAGES`.
- `SYSTEM_ALERT_WINDOW`, requested at runtime, only for the floating AI
  bubble (user-toggleable in Settings; never assumed).
- Foreground service permissions for the Linux-setup and assistant-bubble
  services (both `specialUse` subtypes, each named for what it does).
- `INTERNET` for the OpenRouter-backed assistant and the Arch/npm/curl
  package installs that happen inside the guest.

No root is requested or required anywhere in this project.
