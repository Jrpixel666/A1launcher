package com.a1launcher.linux

import com.a1launcher.model.LOCAL_MODELS
import com.a1launcher.model.LocalModel
import java.io.File

/**
 * Builds and runs [llama.cpp](https://github.com/ggml-org/llama.cpp)'s
 * `llama-server` inside the proot Arch guest, for fully offline AI chat —
 * once a model is downloaded, [LocalLlamaBackend] needs no network at all.
 *
 * The approach (build from source, curated small-model list, an
 * OpenAI-compatible local server) is adapted from
 * [AbuZar-Ansarii/Android-GPU](https://github.com/AbuZar-Ansarii/Android-GPU)
 * (MIT) — see THIRD_PARTY.md. That project runs under Termux, which (like
 * Android itself) uses Bionic libc, so its prebuilt `android-arm64`
 * binaries can't run in this app's Arch Linux ARM guest, which is glibc —
 * hence building from source with Arch's own toolchain here instead of
 * reusing their release binaries. GPU offload (their Vulkan path) isn't
 * wired up either: it needs the device's real Vulkan driver bound into the
 * guest, which this project hasn't set up (see README's GPU note); this
 * builds and runs CPU-only, which is still fast for small models on
 * modern ARM (NEON/dotprod), per their own README's emphasis on exactly that.
 */
object LlamaCpp {

    /** Loopback-only; proot shares the host's network namespace, so the app's own HTTP client reaches this directly. */
    const val PORT = 8090

    private const val REPO_URL = "https://github.com/ggml-org/llama.cpp.git"
    private const val BUILD_PACKAGES = "base-devel cmake clang git"

    fun installDir(proot: Proot) = File(proot.home, "llama.cpp")

    fun serverBinary(proot: Proot) = File(installDir(proot), "build/bin/llama-server")

    fun modelsDir(proot: Proot) = File(proot.home, "models")

    fun isBuilt(proot: Proot) = serverBinary(proot).exists()

    fun isModelDownloaded(proot: Proot, model: LocalModel) = File(modelsDir(proot), model.fileName).exists()

    fun downloadedModels(proot: Proot): List<LocalModel> = LOCAL_MODELS.filter { isModelDownloaded(proot, it) }

    /** Installs build tools, clones, and builds a CPU-only `llama-server`. Idempotent: skips the clone/build if already done. */
    fun buildScript(): String =
        """
        set -e
        pacman -Sy --noconfirm --needed $BUILD_PACKAGES
        if [ ! -d "$GUEST_LLAMA_DIR/.git" ]; then
          git clone --depth 1 $REPO_URL "$GUEST_LLAMA_DIR"
        fi
        cd "$GUEST_LLAMA_DIR"
        cmake -B build -DCMAKE_BUILD_TYPE=Release -DLLAMA_BUILD_SERVER=ON \
          -DGGML_BUILD_TESTS=OFF -DLLAMA_BUILD_TESTS=OFF -DGGML_BUILD_EXAMPLES=OFF
        cmake --build build --target llama-server -j"$(nproc)"
        """.trimIndent()

    fun downloadModelScript(model: LocalModel): String =
        """
        set -e
        mkdir -p "$GUEST_MODELS_DIR"
        curl -fL --retry 3 -o "$GUEST_MODELS_DIR/${model.fileName}.part" "${model.url}"
        mv "$GUEST_MODELS_DIR/${model.fileName}.part" "$GUEST_MODELS_DIR/${model.fileName}"
        """.trimIndent()

    /** The command line to start `llama-server`, run via [Proot.process] and kept as a tracked [Process] so it can be stopped later. */
    fun serverCommand(model: LocalModel): String =
        "\"$GUEST_LLAMA_DIR/build/bin/llama-server\" " +
            "--model \"$GUEST_MODELS_DIR/${model.fileName}\" " +
            "--host 127.0.0.1 --port $PORT -c 4096 --flash-attn on"

    private const val GUEST_LLAMA_DIR = "${Proot.GUEST_HOME}/llama.cpp"
    private const val GUEST_MODELS_DIR = "${Proot.GUEST_HOME}/models"
}
