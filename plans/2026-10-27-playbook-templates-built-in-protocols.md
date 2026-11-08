# Playbook Templates for Built-in Protocols — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #360 — playbook templates for built-in protocols
**Issue group:** #360

**Goal:** Express REQUEST_RESPONSE and TASK_COMPLETION as playbook YAML
templates with metadata documenting exact behavioral semantics, plus
consumer documentation on how to read, fork, and customize templates.

**Architecture:** Two YAML files in `agency-core/src/main/resources/playbook-templates/`
using the playbook envelope format (`playbook: "1.0"`, `schema: protocol`).
Each template contains a `metadata` section (exact rules, config) and a
`states` section (tiered state machine approximation compatible with
`CompiledProtocol.from()`). Consumer documentation added to
`docs/guides/consumer-guide.md`.

**Tech Stack:** YAML (playbook format), Markdown

## Global Constraints

- No Java code — YAML files and documentation only
- State machine uses tiered approximation (IDLE/ACTIVE/OVERLOADED), not exact commitment counting
- Playbook envelope format: multi-document YAML with front-matter
- `schema: protocol` is conventional — not yet registered in `playbook.schema.json`
- Templates are not executable until qhorus#497 lands the ChannelProtocol adapter bridge

---

## Batch 1: Templates and Documentation

### Task 1: Create request-response.playbook.yaml

**Files:**
- Create: `agency-core/src/main/resources/playbook-templates/request-response.playbook.yaml`

**Interfaces:**
- Consumes: Nothing
- Produces: YAML template file for REQUEST_RESPONSE protocol

- [ ] **Step 1: Create the playbook-templates directory**

```bash
mkdir -p agency-core/src/main/resources/playbook-templates
```

- [ ] **Step 2: Write request-response.playbook.yaml**

Create `agency-core/src/main/resources/playbook-templates/request-response.playbook.yaml`
with:

```yaml
---
playbook: "1.0"
schema: protocol
name: request-response
---
metadata:
  description: >
    Monitors the request-response cycle in a channel. Warns when too many
    queries are unanswered, indicating the responder may be overloaded or
    messages are being lost.
  config:
    maxOpenQueries:
      type: int
      default: 3
      override-key: request-response.max-open-queries
  exact-rules:
    - >
      When a QUERY arrives and the count of open (unanswered) QUERY
      commitments >= maxOpenQueries, emit WARNING with action INVESTIGATE
    - >
      When a non-QUERY, non-RESPONSE message arrives and there are any
      open QUERY commitments, emit ADVISORY with action LOG
  behavioral-notes:
    - >
      The state machine uses three tiers: IDLE (0 open queries),
      AWAITING_RESPONSE (1 open query), OVERLOADED (2+ open queries).
      The Java implementation uses a configurable threshold (default 3);
      the state machine triggers WARNING at tier 2 for simplicity.
    - >
      The Java implementation is stateless — it re-derives state from
      active commitments on every evaluation. The playbook state machine
      is stateful and persists via CoordinationStateStore when running.

states:
  IDLE:
    - on:
        QUERY: AWAITING_RESPONSE
        RESPONSE: IDLE

  AWAITING_RESPONSE:
    - advisory:
        severity: INFO
        message: "channel has open queries awaiting response"
        action: LOG
    - on:
        RESPONSE: IDLE
        QUERY: OVERLOADED

  OVERLOADED:
    - advisory:
        severity: WARNING
        message: "multiple unanswered queries — responder may be overloaded"
        action: INVESTIGATE
    - on:
        RESPONSE: AWAITING_RESPONSE
        QUERY: OVERLOADED
```

- [ ] **Step 3: Verify YAML is valid**

```bash
python3 -c 'import yaml, sys; list(yaml.safe_load_all(open(sys.argv[1]))); print("VALID")' agency-core/src/main/resources/playbook-templates/request-response.playbook.yaml
```

Expected: `VALID`

- [ ] **Step 4: Commit**

```bash
git add agency-core/src/main/resources/playbook-templates/request-response.playbook.yaml
git commit -m "feat(#360): add request-response playbook template

Tiered state machine (IDLE → AWAITING_RESPONSE → OVERLOADED) with
metadata documenting exact commitment-counting rules from
RequestResponseProtocol.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 2: Create task-completion.playbook.yaml

**Files:**
- Create: `agency-core/src/main/resources/playbook-templates/task-completion.playbook.yaml`

**Interfaces:**
- Consumes: Nothing
- Produces: YAML template file for TASK_COMPLETION protocol

- [ ] **Step 1: Write task-completion.playbook.yaml**

Create `agency-core/src/main/resources/playbook-templates/task-completion.playbook.yaml`
with:

```yaml
---
playbook: "1.0"
schema: protocol
name: task-completion
---
metadata:
  description: >
    Monitors the task assignment and completion cycle. Warns when too many
    commands are outstanding or when an obligor sends non-completion messages
    while they have open obligations.
  config:
    maxOpenCommands:
      type: int
      default: 3
      override-key: task-completion.max-open-commands
  exact-rules:
    - >
      When a COMMAND arrives and the count of open (incomplete) COMMAND
      commitments >= maxOpenCommands, emit WARNING with action INVESTIGATE
    - >
      When the sender is the obligor on an open COMMAND commitment and
      the incoming message is not DONE, FAILURE, or DECLINE, emit WARNING
      with action INVESTIGATE ("obligor has an open obligation")
  behavioral-notes:
    - >
      The state machine uses three tiers: IDLE (0 open commands),
      TASK_ASSIGNED (1 open command), OVERLOADED (2+ open commands).
      The Java implementation uses a configurable threshold (default 3);
      the state machine triggers WARNING at tier 2 for simplicity.
    - >
      The obligor check (sender has open obligation but sends a
      non-completion message) cannot be represented in the state machine
      without guard expressions — the state machine tracks channel-level
      load, not per-sender obligations. See exact-rules for the full
      specification.
    - >
      The Java implementation is stateless — it re-derives state from
      active commitments on every evaluation. The playbook state machine
      is stateful and persists via CoordinationStateStore when running.

states:
  IDLE:
    - on:
        COMMAND: TASK_ASSIGNED
        DONE: IDLE
        FAILURE: IDLE
        DECLINE: IDLE

  TASK_ASSIGNED:
    - advisory:
        severity: INFO
        message: "channel has open task assignments awaiting completion"
        action: LOG
    - on:
        DONE: IDLE
        FAILURE: IDLE
        DECLINE: IDLE
        COMMAND: OVERLOADED

  OVERLOADED:
    - advisory:
        severity: WARNING
        message: "multiple incomplete tasks — assignee may be overloaded"
        action: INVESTIGATE
    - on:
        DONE: TASK_ASSIGNED
        FAILURE: TASK_ASSIGNED
        DECLINE: TASK_ASSIGNED
        COMMAND: OVERLOADED
```

- [ ] **Step 2: Verify YAML is valid**

```bash
python3 -c 'import yaml, sys; list(yaml.safe_load_all(open(sys.argv[1]))); print("VALID")' agency-core/src/main/resources/playbook-templates/task-completion.playbook.yaml
```

Expected: `VALID`

- [ ] **Step 3: Commit**

```bash
git add agency-core/src/main/resources/playbook-templates/task-completion.playbook.yaml
git commit -m "feat(#360): add task-completion playbook template

Tiered state machine (IDLE → TASK_ASSIGNED → OVERLOADED) with metadata
documenting exact commitment-counting and obligor-check rules from
TaskCompletionProtocol.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 3: Add Protocol Templates section to consumer guide

**Files:**
- Modify: `docs/guides/consumer-guide.md` (append new section before "Key Integration Patterns")

**Interfaces:**
- Consumes: The two template files from Tasks 1-2
- Produces: Documentation section in consumer guide

- [ ] **Step 1: Add Protocol Templates section**

Insert the following section in `docs/guides/consumer-guide.md` before the
`## Key Integration Patterns` heading (currently at line 701):

```markdown
### Protocol Templates (module: `casehub-agency`)

Playbook YAML templates expressing qhorus built-in channel protocols as
state machines. Templates serve as readable specifications, forkable
starting points for custom protocols, and teaching-by-example for the
playbook protocol system.

**Location:** `agency-core/src/main/resources/playbook-templates/`

**Available templates:**

| Template | Source Protocol | States |
|----------|---------------|--------|
| `request-response.playbook.yaml` | `RequestResponseProtocol` (qhorus) | IDLE → AWAITING_RESPONSE → OVERLOADED |
| `task-completion.playbook.yaml` | `TaskCompletionProtocol` (qhorus) | IDLE → TASK_ASSIGNED → OVERLOADED |

**Template structure:**

Each template is a multi-document YAML file with playbook envelope format:

- **Front-matter** (`playbook: "1.0"`, `schema: protocol`, `name`) — identifies
  the template as a protocol playbook
- **`metadata` section** — exact behavioral specification:
  - `description` — what the protocol monitors
  - `config` — configurable parameters with types, defaults, and
    `policyOverrides` keys
  - `exact-rules` — precise specification of when advisories fire,
    matching the Java implementation
  - `behavioral-notes` — where the state machine approximation diverges
    from the Java implementation
- **`states` section** — tiered state machine definition compatible with
  `CompiledProtocol.from()` in platform's `PlaybookProtocolEvaluator`

**Reading a template:**

The `metadata.exact-rules` list describes what the Java protocol actually
does. The `states` section is a simplified state machine approximation —
it uses tiers (IDLE/ACTIVE/OVERLOADED) rather than exact commitment
counts. The `behavioral-notes` explain where and why they differ.

**Creating a custom protocol from a template:**

1. Copy the template to your project
2. Rename the `name` field in the front-matter
3. Modify states, transitions, and advisories to match your protocol
4. Adjust `metadata.config` for your parameters
5. Document your exact rules in `metadata.exact-rules`

**Limitations:**

- Templates use tiered state machine approximations — exact commitment
  counting and per-sender obligation checks require the Java
  implementation
- Templates are not executable as `ChannelProtocol` implementations until
  the adapter bridge in qhorus#497 lands
- The `schema: protocol` value is conventional — not yet registered in
  `playbook.schema.json`
```

- [ ] **Step 2: Verify the guide still reads correctly**

Read the section and surrounding context to ensure it flows with the
existing document structure.

- [ ] **Step 3: Update CLAUDE.md key directories table**

Add the `playbook-templates` directory to the key directories table in
CLAUDE.md:

```
| `agency-core/src/main/resources/playbook-templates/` | Protocol templates — YAML playbook definitions for REQUEST_RESPONSE and TASK_COMPLETION |
```

- [ ] **Step 4: Build to verify resources are packaged**

```bash
mvn --batch-mode compile -pl agency-core
```

Expected: BUILD SUCCESS (YAML files are included in the resources)

- [ ] **Step 5: Commit**

```bash
git add docs/guides/consumer-guide.md CLAUDE.md
git commit -m "feat(#360): add protocol templates section to consumer guide

Documents template structure, how to read templates, and how to create
custom protocols from templates. Updates CLAUDE.md key directories.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## References

- [2026-10-27-playbook-templates-built-in-protocols-design.md] — design spec this plan implements
- RequestResponseProtocol.java (qhorus runtime-core) — source protocol for request-response template
- TaskCompletionProtocol.java (qhorus runtime-core) — source protocol for task-completion template
- PlaybookProtocolEvaluator.java (platform coordination-core) — playbook evaluation engine
- CompiledProtocol.java (platform coordination-core) — state map format reference
- docs/guides/consumer-guide.md — documentation target
- casehubio/agency#360 — focal issue
- casehubio/qhorus#497 — blocks runtime integration
