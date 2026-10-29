package com.a1launcher.model

/**
 * The agent CLIs A1 Launcher knows how to install into the proot Arch environment,
 * plus OpenRouter which is wired in as a hosted-API provider rather than a local CLI.
 * Each entry names the asset script under assets/scripts/agents/ that performs its install.
 */
enum class AgentCli(
    val id: String,
    val displayName: String,
    val description: String,
    val scriptAsset: String,
    val isHostedProvider: Boolean = false,
) {
    CLAUDE(
        id = "claude",
        displayName = "Claude Code",
        description = "Anthropic's agentic coding CLI.",
        scriptAsset = "agents/install-claude.sh",
    ),
    OPENCODE(
        id = "opencode",
        displayName = "opencode",
        description = "Open-source terminal coding agent.",
        scriptAsset = "agents/install-opencode.sh",
    ),
    CODEX(
        id = "codex",
        displayName = "Codex CLI",
        description = "OpenAI's terminal coding agent.",
        scriptAsset = "agents/install-codex.sh",
    ),
    GROK(
        id = "grok",
        displayName = "Grok CLI",
        description = "xAI Grok terminal agent.",
        scriptAsset = "agents/install-grok.sh",
    ),
    COPILOT(
        id = "copilot",
        displayName = "GitHub Copilot CLI",
        description = "GitHub's terminal Copilot agent.",
        scriptAsset = "agents/install-copilot.sh",
    ),
    CRUSH(
        id = "crush",
        displayName = "Crush",
        description = "Charm's multi-model terminal agent.",
        scriptAsset = "agents/install-crush.sh",
    ),
    CURSOR_AGENT(
        id = "cursor-agent",
        displayName = "cursor-agent",
        description = "Cursor's terminal coding agent.",
        scriptAsset = "agents/install-cursor-agent.sh",
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        description = "Bring-your-own-key access to many hosted models, used by A1's built-in assistant.",
        scriptAsset = "agents/configure-openrouter.sh",
        isHostedProvider = true,
    );

    companion object {
        fun localClis(): List<AgentCli> = entries.filterNot { it.isHostedProvider }
    }
}
