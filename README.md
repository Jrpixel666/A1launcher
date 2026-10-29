# A1 Launcher

An Android home launcher (Kotlin + Jetpack Compose, Material 3 dynamic
color) with a built-in AI agent assistant, home-screen widgets, and an
optional on-device Linux environment (Arch Linux ARM under `proot`, no
root) with a choice of AI coding-agent CLIs installed via
[mise](https://mise.jdx.dev). **A1 Launcher is its own Wayland compositor**
— every Linux GUI window opens as its own Android task, with its own card
in Recents, exactly as the original task asked for.

That last part — and the real `proot` binaries, and the mise-based agent
installer — are adapted from [Omalauncher](https://github.com/SimonSchubert/omalauncher)
(MIT), which solves this exact problem for real. See **Credit** below and
`THIRD_PARTY.md` for exactly what was adapted and how.

## Module layout

```
com.a1launcher
├── A1LauncherApp.kt          Application class; holds SettingsRepository + LinuxRuntime
├── home/                     The launcher surface itself (HOME activity)
│   ├── MainActivity.kt       Compose host, widget-picker result handling
│   ├── HomeScreen.kt         Widget area + dock (Apps / Linux / Settings)
│   ├── AppDrawer.kt          Full app grid (queries PackageManager)
│   └── WidgetHostManager.kt  Real AppWidgetHost wrapper — hosts ANY widget
├── widget/                   A1's own Glance widget (agent/Linux status)
├── setup/                    First-launch wizard (welcome → Linux opt-in
│                             → agent picker → review → install → done)
├── ui/theme/A1Theme.kt       Material 3, dynamicDarkColorScheme on API 31+
├── linux/                    proot/Arch/compositor orchestration
│   ├── Proot.kt              Paths, proot flags, PROOT_LOADER/LD_LIBRARY_PATH env
│   ├── Bootstrap.kt          Download/extract Arch Linux ARM, configure, drop the kernel
│   ├── Agents.kt             The Agent list + mise-based install (AgentStore)
│   ├── ProcShim.kt           /proc stand-ins Android hides from apps (btop etc.)
│   ├── LinuxRuntime.kt       The Wayland server + window manager, process-wide
│   ├── ProotInstallService.kt  Foreground service driving Bootstrap + AgentStore
│   ├── LlamaCpp.kt           Builds llama.cpp + runs llama-server, for offline AI
│   ├── LlamaServerRuntime.kt Starts/stops the local llama-server process
│   └── LlamaCppInstallService.kt  Foreground service driving the llama.cpp build
├── wayland/                  The Wayland compositor itself (no Sway/Hyprland/Qtile needed)
│   ├── WaylandServer.kt / Compositor.kt / XdgShell.kt / Input.kt / Seat.kt / ...
│   ├── WindowManager.kt      One Android task per toplevel — see below
│   ├── WindowRenderer.kt     Draws one window into its activity's SurfaceView
│   └── Native.kt             JNI bindings to cpp/wlbridge.c
├── linuxdesktop/LinuxWindowActivity.kt  One Linux window = one Android task
├── assistant/                The AI agent chat overlay
│   ├── AgentBackend.kt / OpenRouterBackend.kt / LocalLlamaBackend.kt
│   ├── OpenAiCompatible.kt   Shared OpenAI-style chat-completions client
│   ├── AssistantViewModel.kt / AssistantChatScreen.kt / AssistantChatActivity.kt
│   └── AssistantOverlayService.kt  Draggable floating bubble
├── settings/
│   ├── SettingsActivity.kt   Also hosts the "Offline AI" install/model/start-stop section
│   └── LocalAiViewModel.kt
├── data/                     DataStore + EncryptedSharedPreferences
└── model/                    InstallState, ChatMessage

app/src/main/cpp/wlbridge.c   JNI: unix sockets + shm + compositing into ANativeWindow
scripts/fetch-proot.sh        Builds proot from Termux's source -> app/src/main/jniLibs
patches/proot/                The one patch fetch-proot.sh applies
```

## Design decisions (and why)

### No Sway, Hyprland, or Qtile — A1 Launcher is the compositor

The original design (first pass of this repo) planned to install Sway
*inside* the guest and show its whole session as one Android task. Having
since read Omalauncher's actual source, that's the wrong shape for "each
Linux app opens as its own Android task": a traditional WM/compositor
(Sway, Hyprland, Qtile's Wayland backend) *is itself* the one thing that
would stand between a guest app's window and its own Recents card. So
instead, `wayland/` implements a minimal Wayland protocol server directly
in Kotlin + JNI (`wl_compositor`, `wl_subcompositor`, `wl_shm`, `wl_output`,
`wl_seat`, `wl_data_device_manager`, `xdg_wm_base`,
`zxdg_decoration_manager_v1`, `zwp_text_input_manager_v3`) — the launcher
*is* the compositor, and `WindowManager.toplevelCreated()` is the hook that
turns a new guest window directly into a new Android task. Ordinary
Wayland apps (foot, GTK apps) connect to it with no desktop environment
installed in the guest at all.

(If you ever want a traditional tiling WM back — e.g. to collapse multiple
guest windows into one merged desktop task instead of one-task-per-window —
Sway or Qtile's Wayland backend could run *nested*, as a nested-Wayland
client of this same compositor; Hyprland would lose its GPU-accelerated
compositing doing that, since this compositor only offers `wl_shm`, not
`linux-dmabuf`. Not implemented; see Omalauncher's `ui/WindowManager.kt`
for the real per-window hook this project's `wayland/WindowManager.kt`
is adapted from.)

### The per-window-task mechanism

One Activity class, `LinuxWindowActivity`, declared `documentLaunchMode="always"`
with `taskAffinity=""`. `LinuxRuntime.openWindowActivity()` launches it with
`FLAG_ACTIVITY_NEW_DOCUMENT or FLAG_ACTIVITY_MULTIPLE_TASK` and a unique
`a1launcher://window/<handle>` data URI per toplevel — standard Android
APIs (the same mechanism Chrome uses for tabs, or Drive for documents), so
every Linux window gets a real, separate Recents card with no faking.

### Kernel packages: blocked outright, and actually removed

`Bootstrap.kt`'s `configure()` step sets `NoExtract` for
`usr/lib/firmware/*` and `usr/lib/modules/*` in `pacman.conf` (so future
installs/upgrades skip them) **and** a dedicated step removes the kernel
and firmware the Arch Linux ARM base tarball ships pre-installed
(`pacman -Rdd linux-aarch64 linux-firmware...`) — under proot there's no
real kernel in the guest (every process shares the host Android kernel),
so both are dead weight at best. The removal step matters: pacman.conf
alone would leave the ~1 GB the base image already shipped in place.

### Agent CLIs, via mise

Every agent CLI in `linux/Agents.kt`'s `AGENTS` list installs through
[mise](https://mise.jdx.dev), a universal tool-version manager, instead of
one hand-rolled shell script per tool. This is a straight improvement over
this repo's first pass (separate `pacman`/`npm`/`curl` scripts per agent):
one install path for npm-backed, GitHub-release-backed, and custom-URL
agents alike, with `mise self-update`/cache-cleanup already handled.

It also resolves every name from the original task's agent list, including
the ones this repo's first pass had to drop as unidentifiable — reading
Omalauncher's own `THIRD_PARTY.md` (for its bundled icon credits) turned up
real names and install specs for all of them:

| name | is | mise package |
|---|---|---|
| `agy` | Antigravity (Google) | `antigravity-cli` |
| `pi` | Pi (pi.dev) | `pi` |
| `omp` | Oh My Pi (can1357) | `github:can1357/oh-my-pi` |
| `ori` | Ori (OpenRouterLabs' own CLI agent — not the OpenRouter API key) | `github:OpenRouterLabs/ori-releases` |
| `muse` | Muse Code (Meta) | custom installer URL |
| `kimi` | Kimi (Moonshot AI) | `npm:@moonshot-ai/kimi-code` |

Plus `claude`, `opencode`, `codex`, `grok`, `copilot`, `crush`,
`cursor-agent`. **OpenRouter** stays separate from this list — it's a
hosted-API key for A1's own in-app assistant (`OpenRouterBackend.kt`), not
a guest CLI.

### Offline AI: llama.cpp, built in the guest

A Dock shortcut ("Offline AI", next to "Linux") and a matching section in
Settings build [llama.cpp](https://github.com/ggml-org/llama.cpp) from
source inside the proot guest, download one of a few small GGUF instruct
models, and run its `llama-server` on `127.0.0.1:8090`. `LocalLlamaBackend`
talks to that exactly like `OpenRouterBackend` talks to OpenRouter's API —
same OpenAI-compatible request/response shape (`OpenAiCompatible.kt`),
just no API key and no network once the model is downloaded. Toggling
"Use for assistant chat" in Settings makes `AssistantViewModel` prefer it
over OpenRouter.

The approach — build from source, curated small-model list, local
OpenAI-compatible server — is adapted from
[AbuZar-Ansarii/Android-GPU](https://github.com/AbuZar-Ansarii/Android-GPU)
(MIT; see THIRD_PARTY.md), which does the same thing under Termux. Not
reused as-is: that project's prebuilt `android-arm64` release binaries are
built against Bionic (Android's/Termux's libc) and won't run in this
project's glibc-based Arch guest, so this builds from source with Arch's
own toolchain instead; its Vulkan GPU-offload path also isn't wired up
here, for the same reason as the rest of this project's GPU story (see
below) — this runs CPU-only.
hosted-API key for A1's own in-app assistant (`OpenRouterBackend.kt`), not
a guest CLI.

## What's real, and what's still a deliberate gap

**Real and working, verified to build an APK from this exact source tree:**
home screen UI, app drawer, third-party widget hosting, A1's own Glance
widget, the setup wizard end to end, Material 3 dynamic color, the floating
AI-agent bubble, the in-app assistant chat against OpenRouter's real HTTP
API, the full proot/Bootstrap/mise-agent-install pipeline, the real Wayland
compositor and its native bridge, and the per-window Android-task
mechanism. The `proot` binaries themselves are **real, built binaries**
(`scripts/fetch-proot.sh` builds them from Termux's pinned proot source
with the NDK, into `app/src/main/jniLibs/arm64-v8a/`), not a documented gap
— that's the one thing the first pass of this repo couldn't do without
Omalauncher's build recipe.

**What's not wired up, on purpose, scoped out of this pass rather than
faked:**
- **Keyboard input into guest windows.** `wayland/Input.kt` has `key()`
  ready for it; `LinuxWindowActivity` forwards touch (tap, drag) but not
  physical/soft keyboard events yet.
- **GPU acceleration.** Guest apps render in software (no Vulkan/GL
  passthrough); Omalauncher's `Gpu.kt`/pinned driver blobs for Mali/Adreno
  weren't ported. Fine for terminals and most CLI-driven GUI tools; slow
  for anything GPU-heavy.
- **A Linux app list / desktop entries.** Installed agents run via
  `LinuxRuntime.openAgent()`/`openTerminal()`, not from a dedicated Linux
  app drawer; Omalauncher's `managed()`/`.desktop`-entry generation and
  icon bundling weren't ported.
- **Cross-app-update step tracking.** `Bootstrap.run()` re-checks simple
  on-disk markers (does `pacman` exist, does `foot` exist, does the keymap
  exist) rather than Omalauncher's full `StepJournal`/`Provisioner` system,
  so a step whose *implementation* changes without changing what marker it
  checks won't re-run automatically on an existing install.

## Credit

The Wayland compositor (`wayland/`, `cpp/wlbridge.c`), the real `proot`
build recipe (`scripts/fetch-proot.sh`, `patches/proot/`), the per-window
Android-task mechanism (`wayland/WindowManager.kt`,
`linuxdesktop/LinuxWindowActivity.kt`), and the mise-based agent installer
(`linux/Agents.kt`, `linux/Bootstrap.kt`, `linux/Proot.kt`, `linux/ProcShim.kt`,
`linux/Shell.kt`, `linux/Io.kt`) are adapted from
[Omalauncher](https://github.com/SimonSchubert/omalauncher) by Simon
Schubert and contributors, MIT licensed. See `THIRD_PARTY.md` for the full
license text and a per-file note on what was trimmed or changed. Everything
not listed there — the launcher UI, widgets, setup wizard, assistant
overlay, settings — is this project's own.

The offline/local AI feature (`linux/LlamaCpp.kt`, `linux/LlamaServerRuntime.kt`,
`linux/LlamaCppInstallService.kt`, `assistant/LocalLlamaBackend.kt`) adapts
the *approach* taken by
[AbuZar-Ansarii/Android-GPU](https://github.com/AbuZar-Ansarii/Android-GPU)
(MIT) — building llama.cpp from source and running its OpenAI-compatible
server locally — into A1 Launcher's own `AgentBackend`/Compose UI, rather
than reusing its code directly (it's a Termux-targeted Python/Flask app
with its own web UI). See THIRD_PARTY.md for the license text and exactly
what carried over (the curated model list, the `llama-server` CLI flags).

## Building

`./gradlew assembleDebug` needs `ANDROID_HOME` pointed at an Android SDK
with platform 34 + build-tools 34.0.0, **and** NDK 30.0.16248370 (set via
`ndkVersion` in `app/build.gradle.kts`, matching `scripts/fetch-proot.sh`'s
`NDK_VERSION`) to compile `cpp/wlbridge.c`. Verified to produce a working
debug APK from this exact source tree, including the real native libs.

Before a release build, run `scripts/fetch-proot.sh` (needs `bsdtar` and
the NDK above) to (re)populate `app/src/main/jniLibs/arm64-v8a/`; without
it every Linux feature fails at runtime with a clear "proot binary
missing" condition rather than a crash.

`.github/workflows/build.yml` assembles a debug APK on GitHub Actions and
uploads it as a workflow artifact on every push, running `fetch-proot.sh`
first so the uploaded APK has working Linux support, not just a compiling
build.

## Permissions this app requests

- Package-visibility `<queries>` for `LAUNCHER` activities (app drawer) —
  not the broader `QUERY_ALL_PACKAGES`.
- `SYSTEM_ALERT_WINDOW`, requested at runtime, only for the floating AI
  bubble (user-toggleable in Settings; never assumed).
- Foreground service permissions for the Linux-setup service (`specialUse`,
  named for what it does).
- `INTERNET` for the OpenRouter-backed assistant and the Arch Linux
  ARM/mise package installs that happen inside the guest.

No root is requested or required anywhere in this project.
