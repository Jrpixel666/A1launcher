package com.a1launcher.model

/** Progress of building llama.cpp + downloading a model, reported by LlamaCppInstallService. */
sealed class LocalAiStep {
    data object NotStarted : LocalAiStep()
    data object InstallingBuildTools : LocalAiStep()
    data object CloningLlamaCpp : LocalAiStep()
    data object Building : LocalAiStep()
    data class DownloadingModel(val modelName: String) : LocalAiStep()
    data object Done : LocalAiStep()
    data class Failed(val message: String) : LocalAiStep()
}

data class LocalAiProgress(
    val step: LocalAiStep = LocalAiStep.NotStarted,
    val fraction: Float? = null,
    val logTail: List<String> = emptyList(),
)

/** A small GGUF chat model llama-server can run, curated for phone-class RAM. */
data class LocalModel(
    val id: String,
    val label: String,
    val approxSize: String,
    val fileName: String,
    val url: String,
)

/**
 * A handful of small instruct models, picked for being usable on phone-class
 * RAM (same family of choices as Android-GPU's curated list — see
 * THIRD_PARTY.md). Bigger GGUFs work too if copied into the model directory
 * by hand; this list is just what the install flow offers by default.
 */
val LOCAL_MODELS = listOf(
    LocalModel(
        id = "smollm2-135m",
        label = "SmolLM2 135M",
        approxSize = "~100 MB",
        fileName = "SmolLM2-135M-Instruct-Q4_0.gguf",
        url = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_0.gguf",
    ),
    LocalModel(
        id = "qwen2.5-0.5b",
        label = "Qwen2.5 0.5B",
        approxSize = "~400 MB",
        fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
    ),
    LocalModel(
        id = "llama-3.2-1b",
        label = "Llama 3.2 1B",
        approxSize = "~800 MB",
        fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
    ),
    LocalModel(
        id = "qwen2.5-coder-3b",
        label = "Qwen2.5 Coder 3B",
        approxSize = "~1.9 GB",
        fileName = "qwen2.5-coder-3b-instruct-q4_k_m.gguf",
        url = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
    ),
)
