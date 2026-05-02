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

### Agent Teams (s09)

Persistent teammates that communicate via JSONL inboxes. Each teammate runs in its own thread with a full agent loop.

**Directory structure:**
```
.team/
  config.json           # team roster + status
  inbox/
    alice.jsonl         # append-only message queue
    bob.jsonl
    lead.jsonl
  protocols/            # s10: protocol request tracking
    shutdown_requests.jsonl
    plan_approval_requests.jsonl
```

**Tools:**
- `spawn_teammate(name, role, prompt)` — create a persistent teammate (parentOnly)
- `list_teammates()` — show all teammates and their status (parentOnly)
- `broadcast(content)` — send message to all teammates (parentOnly)
- `send_message(to, content, msg_type?)` — send message to specific teammate or lead (baseTools)
- `read_inbox()` — read and clear your inbox (baseTools)

**Lifecycle:**
```
spawn -> WORKING (50 iterations max) -> IDLE (thread ends)
       ↑                                      |
       +-------- re-spawn to reactivate ------+
```

**Communication:**
- Lead automatically checks inbox at the start of each agent cycle
- Teammates check inbox at the start of each iteration
- Messages are JSON objects: `{type, from, content, timestamp, protocol_version, metadata}`
- Valid types: `MESSAGE`, `BROADCAST`, `SHUTDOWN_REQUEST:v1`, `SHUTDOWN_RESPONSE:v1`, `PLAN_REQUEST:v1`, `PLAN_RESPONSE:v1`, `REQUEST_CANCELLED:v1`

**Teammate tools:**
- baseTools: bash, read_file, write_file, edit_file
- communication: send_message, read_inbox
- NOT available: spawn_teammate, list_teammates, broadcast, task, compact

### Team Protocols (s10)

Request-Response协议模式，支持优雅关机和计划审批。

**Shutdown Protocol:**
```
Lead                    Teammate
  |                        |
  |--shutdown_request----->|
  | {req_id: "abc"}        |
  |                        |
  |<--shutdown_response----|
  | {req_id: "abc",        |
  |  approve: true}        |
```

**Plan Approval Protocol:**
```
Teammate                Lead
  |                        |
  |--plan_request--------->|
  | {req_id: "xyz",        |
  |  plan: "..."}          |
  |                        |
  |<--plan_response--------|
  | {req_id: "xyz",        |
  |  approve: true}        |
```

**Protocol Tools (Lead only):**
- `shutdown_request(teammate)` — request teammate to shut down gracefully
- `plan_response(request_id, approve, feedback?)` — approve or reject a plan
- `list_pending_requests()` — show all pending protocol requests
- `cancel_request(request_id)` — cancel a pending request

**Protocol Tools (Teammate only):**
- `shutdown_response(request_id, approve, reason?)` — respond to shutdown request
- `plan_request(plan)` — submit plan for approval before high-risk operations

**Features:**
- Request tracking with unique IDs
- Automatic timeout (30s for shutdown, 5min for plan approval)
- Persistent protocol state in `.team/protocols/*.jsonl`
- Protocol versioning for future compatibility
- FSM states: pending → approved/rejected/timeout/cancelled
