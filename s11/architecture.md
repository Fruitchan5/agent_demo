# S11 Autonomous Agents - Architecture Overview

## What S11 Is

S11 transforms teammates from **passive workers** into **autonomous agents** that find their own work. The core insight: "The agent finds work itself."

Instead of waiting to be assigned tasks, teammates actively poll for work when idle. They check their inbox for messages and scan a shared task board for unclaimed tasks, automatically claiming and executing work they discover.

## The Problem It Solves

**Before S11 (s09/s10):**
- Teammates were reactive: spawn → work on assigned task → idle → thread ends
- Lead had to explicitly assign work to each teammate
- Idle teammates consumed resources but contributed nothing
- No mechanism for work distribution beyond direct assignment

**After S11:**
- Teammates are proactive: when idle, they search for work
- Task board enables decoupled work distribution
- Idle time becomes productive (polling for new work)
- Teammates can pick up work without lead intervention

This enables true multi-agent autonomy where the lead creates tasks and teammates self-organize to complete them.

## Core Mechanisms

### 1. Idle Polling Loop

The heart of autonomy. After completing work, instead of shutting down, teammates enter an IDLE phase:

```
IDLE phase (60s timeout, poll every 5s):
  ├─ Check inbox → message found? → resume WORK
  ├─ Scan .tasks/ → unclaimed task? → claim + resume WORK  
  └─ Timeout (60s) → shutdown
```

**Location:** `TeammateManager._loop()` lines 267-303

**Key parameters:**
- `POLL_INTERVAL = 5` seconds between checks
- `IDLE_TIMEOUT = 60` seconds before giving up
- Polls = 12 iterations (60s / 5s)

### 2. Task Board + Auto-claiming

Shared filesystem-based task queue at `.tasks/task_*.json`:

```json
{
  "id": 1,
  "subject": "Fix login bug",
  "description": "...",
  "status": "pending",
  "owner": null,
  "blockedBy": []
}
```

**Claiming logic** (lines 139-156):
- Lock-protected to prevent race conditions (`_claim_lock`)
- Validates: task exists, not already owned, status=pending, not blocked
- Atomically sets owner + status=in_progress

**Scanning logic** (lines 127-136):
- Finds tasks where: `status == "pending" AND owner == null AND blockedBy == []`
- Returns sorted list (by filename, so by task ID)
- Teammate claims first unclaimed task found

### 3. Identity Re-injection After Compression

When context gets compressed (messages list shrinks), agents lose their identity. S11 re-injects it:

```python
def make_identity_block(name: str, role: str, team_name: str) -> dict:
    return {
        "role": "user",
        "content": f"<identity>You are '{name}', role: {role}, team: {team_name}. Continue your work.</identity>",
    }
```

**Trigger condition** (line 292): `if len(messages) <= 3`

When messages list is short (indicating compression happened), inject identity block at position 0, followed by acknowledgment at position 1, then append the claimed task.

### 4. Thread Lifecycle

```
spawn (status: working)
  ↓
WORK phase (50 iterations max)
  ├─ Check inbox each iteration
  ├─ Call LLM with tools
  ├─ Execute tool calls
  └─ Break if: stop_reason != "tool_use" OR idle tool called
  ↓
IDLE phase (60s timeout)
  ├─ Poll inbox + task board every 5s
  └─ Resume WORK if work found
  ↓
shutdown (status: shutdown)
```

**Status transitions:**
- `working` → `idle` → `working` (if work found)
- `working` → `idle` → `shutdown` (if timeout)
- `working` → `shutdown` (if shutdown_request received)

## Key Differences from S09/S10

| Aspect | S09/S10 | S11 |
|--------|---------|-----|
| **Work model** | Passive: wait for assignment | Active: search for work |
| **Idle behavior** | Thread ends immediately | Poll for 60s before shutdown |
| **Task distribution** | Lead assigns via messages | Self-service from task board |
| **Thread lifecycle** | spawn → work → end | spawn → work → idle → (resume or end) |
| **Autonomy** | Zero (fully directed) | High (self-organizing) |

**What S10 added:** Protocol system (shutdown_request, plan_approval) for structured communication

**What S11 adds:** Autonomy via idle polling + task board auto-claiming

## Most Important Mechanisms to Understand

### 1. **Idle Polling Loop** (Lines 267-303)
The autonomy engine. Understand:
- Why 5s interval? (Balance responsiveness vs. API cost)
- Why 60s timeout? (Don't keep idle threads forever)
- Order of checks: inbox first (reactive), then task board (proactive)

### 2. **Task Board Concurrency** (Lines 139-156)
The coordination primitive. Understand:
- Why lock-protected claiming? (Prevent double-claiming)
- Why atomic status update? (Ensure consistency)
- What happens if two agents claim simultaneously? (Lock serializes, second gets error)

### 3. **Identity Re-injection** (Lines 159-164, 292-294)
The memory mechanism. Understand:
- Why needed? (Context compression loses identity)
- When triggered? (When messages list is short)
- What gets injected? (Identity block + acknowledgment + task)

### 4. **Work Phase vs. Idle Phase** (Lines 226-303)
The state machine. Understand:
- What breaks work phase? (stop_reason != tool_use OR idle tool called)
- What resumes work phase? (Inbox message OR unclaimed task found)
- Why 50 iteration limit in work phase? (Prevent infinite loops)

## Architecture Patterns

**Pattern 1: Filesystem as Message Bus**
- `.team/inbox/*.jsonl` for messages
- `.tasks/task_*.json` for work queue
- Simple, durable, inspectable

**Pattern 2: Polling-Based Coordination**
- No pub/sub, no webhooks
- Simple polling loop with sleep
- Trade latency for simplicity

**Pattern 3: Optimistic Concurrency**
- Scan without locks (read-only)
- Lock only during claim (write)
- Retry on conflict (continue to next task)

**Pattern 4: Stateful Threads**
- Each teammate = one thread with persistent message history
- Thread survives across work/idle transitions
- Context accumulates until compression

## Next Steps for Deep Dive

After understanding this overview, explore:

1. **Concurrency**: How does lock-based claiming scale? What are race condition scenarios?
2. **Context Management**: When does compression trigger? How is identity preserved?
3. **Observability**: How do you monitor idle agents? How do you debug stuck agents?
4. **Scaling**: What happens with 10 agents polling the same task board? 100?

---

**Source:** `D:\ProgramData\Agent\learn-claude-code\agents\s11_autonomous_agents.py`
**Date Analyzed:** 2026-05-02
