# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build (skip tests)
mvn package -DskipTests

# Run
java -jar target/agent_demo-1.0-SNAPSHOT.jar

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ContextCompactorTest
```

## Configuration

Copy `.env.example` to `.env` and fill in:

```
ANTHROPIC_API_KEY=...
MODEL_ID=claude-sonnet-4-6          # or any Anthropic-compatible model
ANTHROPIC_BASE_URL=...              # omit for direct Anthropic API

# Optional tuning
COMPACT_TOKEN_THRESHOLD=50000       # Layer 2 auto-compact trigger (tokens)
COMPACT_KEEP_RECENT=3               # Layer 1 keeps this many recent tool_results intact
TRANSCRIPT_DIR=.transcripts         # where JSONL session logs are written
MCP_COMMAND=...                     # shell command to launch an MCP server (optional)
```

`AppConfig` reads `.env` via dotenv-java, with system env vars taking precedence.

## Architecture

The project is a Spring-free CLI Agent framework built on the Anthropic Messages API.

### Core loop

`AgentLoop` owns the `chatHistory` list and drives the agentic cycle:
1. Before each LLM call, `ContextCompactor.microCompact()` (Layer 1) silently replaces old `tool_result` content with `[Previous: used <tool>]` placeholders, keeping only the last `COMPACT_KEEP_RECENT` results intact.
2. If `estimateTokens(chatHistory) > COMPACT_TOKEN_THRESHOLD`, Layer 2 fires: `ContextCompactor.autoCompact()` calls the LLM to summarize the full history, saves a JSONL transcript via `TranscriptManager`, and replaces `chatHistory` with a single `[Compressed]` summary message.
3. The LLM can also trigger Layer 3 by calling the `compact` tool, which routes through `CompactTool` → returns `CompactTool.COMPACT_SIGNAL` → `AgentLoop` calls `autoCompact()` directly.

### Tool system

- `AgentTool` interface: `name()`, `description()`, `inputSchema()`, `execute(Map<String,Object>)`.
- `ToolRegistry` holds `baseTools` (shared by parent + sub-agents) and `parentOnlyTools` (e.g. `TaskTool`). `CompactTool` is registered via reflection so the code compiles without it.
- `ToolManager` wraps `ToolRegistry`, adds todo-accountability reminders, and serializes tools to the LLM's `tools` array.
- All file-system tools go through `WorkspaceEnv.safePath()`, which rejects any path that escapes `user.dir`.

### Parent / sub-agent split

`TaskTool` (parent-only) spawns a `DefaultSubAgentRunner` with a fresh `AgentContext` and a restricted tool set (no `task` tool, preventing recursive dispatch). The sub-agent runs its own loop and returns a plain-text summary; it never writes back to the parent's `chatHistory`.

### Skill system

Skills live under `skills/<name>/SKILL.md` with YAML frontmatter (`name`, `description`, `trigger`). `SkillLoader` scans the directory at startup. `LoadSkillTool` injects a skill's body into the conversation on demand, avoiding system-prompt bloat.

### Observability

`MonitoredTool` wraps every tool call and records timing + success/failure into `SessionStats` (thread-local singleton). `MonitorLogger.printStats()` prints a summary table; `flushToFile()` writes a full JSON session log to `.logs/session_<id>.json`.

### MCP integration

If `MCP_COMMAND` is set, `McpClient` launches the process and speaks JSON-RPC over stdio. `McpToolAdapter` wraps discovered MCP tools as `AgentTool` instances and registers them into `ToolRegistry`.
