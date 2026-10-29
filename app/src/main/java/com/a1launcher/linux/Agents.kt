package com.a1launcher.linux

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * A coding agent CLI, installed the way Omarchy's omarchy-default-agent does
 * it: via mise, a universal tool-version manager, rather than a one-off
 * shell script per tool.
 *
 * Adapted from Omalauncher's `linux/Agents.kt` (MIT). Its THIRD_PARTY.md is
 * also where the names from the original task description that weren't
 * identifiable from the name alone (agy, pi, omp, ori, muse, kimi) turned
 * out to be real, installable tools — see each one's comment below.
 */
data class Agent(
    val bin: String,
    val name: String,
    val description: String,
    /** mise package spec. */
    val pkg: String,
    /** What starts it without a prompt, when that's more than [bin]. */
    val command: String = bin,
    val node: Boolean = pkg.startsWith("npm:"),
) {
    /** Its activity alias in the manifest, so Recents names its window after it. */
    val windowAlias get() = bin.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) } + "Window"
}

val AGENTS =
    listOf(
        Agent("claude", "Claude Code", "Anthropic's agentic coding CLI.", "claude"),
        // mise installs OpenCode 2 from npm (@opencode/cli).
        Agent("opencode", "opencode", "Open-source terminal coding agent.", "opencode", node = true),
        Agent("codex", "Codex CLI", "OpenAI's terminal coding agent.", "codex"),
        Agent("grok", "Grok CLI", "xAI's terminal agent.", "npm:@xai-official/grok"),
        Agent("copilot", "GitHub Copilot CLI", "GitHub's terminal Copilot agent.", "copilot"),
        Agent("crush", "Crush", "Charm's multi-model terminal agent.", "crush"),
        Agent("cursor-agent", "cursor-agent", "Cursor's terminal coding agent.", "cursor-agent"),
        Agent("agy", "Antigravity", "Google's terminal coding agent.", "antigravity-cli"),
        Agent("pi", "Pi", "pi.dev's terminal coding agent.", "pi"),
        Agent("omp", "Oh My Pi", "can1357's wrapper around Pi.", "github:can1357/oh-my-pi"),
        Agent("ori", "Ori", "OpenRouterLabs' terminal coding agent (not the OpenRouter API key below).", "github:OpenRouterLabs/ori-releases", command = "ori code"),
        Agent(
            "muse",
            "Muse Code",
            "Meta's terminal coding agent.",
            "http:muse[url=https://api.meta.ai/muse-launcher.sh,bin=muse,version_list_url=https://api.meta.ai/muse-code/channels/muse-stable,version_json_path=.version]",
        ),
        Agent("kimi", "Kimi", "Moonshot AI's terminal coding agent.", "npm:@moonshot-ai/kimi-code"),
    )

const val MISE_SELF_UPDATE = "{ mise self-update -y || true; }"

const val MISE_CLEANUP = "mise cache clear && rm -rf ~/.local/share/mise/downloads ~/.npm/_cacache"

/**
 * Starts [agentBin] with a first prompt; a few agents need their own flag
 * shape instead of a bare argument.
 */
fun agentInvocation(
    agentBin: String,
    prompt: String,
    readOnly: Boolean = false,
): String {
    val quoted = shellQuote(if (prompt.startsWith("-")) " $prompt" else prompt)
    return when {
        agentBin == "opencode" -> "$agentBin --prompt $quoted"
        agentBin == "copilot" -> "$agentBin -i $quoted"
        agentBin == "claude" && readOnly -> "$agentBin --permission-mode plan $quoted"
        agentBin == "ori" -> "ori code --interactive --prompt $quoted"
        agentBin == "kimi" -> "kimi --prompt $quoted && kimi --continue"
        agentBin == "agy" && readOnly -> "$agentBin --mode plan -i $quoted"
        agentBin == "agy" -> "$agentBin -i $quoted"
        else -> "$agentBin $quoted"
    }
}

class AgentStore(
    private val proot: Proot,
    private val onChanged: () -> Unit = {},
) {
    private val mise get() = File(proot.home, ".local/bin/mise")
    private val shims get() = File(proot.home, ".local/share/mise/shims")

    val states = mutableStateMapOf<String, JobState>()

    /** Shims are symlinks to a guest path, so don't follow them from the host. */
    fun isInstalled(agent: Agent) = Files.exists(File(shims, agent.bin).toPath(), LinkOption.NOFOLLOW_LINKS)

    private val preferredFile get() = File(proot.launcherConfig, "agent")
    private var preferred by mutableStateOf<String?>(null)

    var installed by mutableStateOf<List<Agent>>(emptyList())
        private set

    fun load() {
        preferred = runCatching { preferredFile.readText().trim() }.getOrNull()
        refresh()
    }

    private fun refresh() {
        installed = AGENTS.filter { isInstalled(it) }
    }

    val default: Agent? get() = installed.firstOrNull { it.bin == preferred } ?: installed.firstOrNull()

    fun choose(agent: Agent) {
        preferred = agent.bin
        runCatching {
            preferredFile.parentFile?.mkdirs()
            preferredFile.writeText(agent.bin + "\n")
        }.onFailure { Log.w("agents", "cannot save the default agent", it) }
    }

    /** Installs [agent] with mise, bootstrapping mise itself if needed. */
    private fun installScript(agent: Agent) =
        buildString {
            append(if (mise.exists()) "$MISE_SELF_UPDATE && " else "curl -fsSL https://mise.run | sh && ")
            if (agent.node) append("mise use -g node@lts && ")
            append("mise use -g ${shellQuote(agent.pkg)} && $MISE_CLEANUP")
        }

    suspend fun install(agent: Agent): Boolean =
        run(agent, "installing ${agent.name}") { installScript(agent) }.also { ok ->
            if (ok) {
                refresh()
                onChanged()
            }
        }

    private suspend fun run(
        agent: Agent,
        action: String,
        script: () -> String,
    ): Boolean {
        val (ok, _) = proot.trackedJob(action, withContext(Dispatchers.IO) { script() }) { states[agent.bin] = it }
        Log.i("agents", "${agent.bin}: ${if (ok) "ok" else "failed"}")
        return ok
    }
}
