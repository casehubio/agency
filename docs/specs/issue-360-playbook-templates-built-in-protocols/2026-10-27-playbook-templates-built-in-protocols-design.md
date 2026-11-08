# Playbook Templates for Built-in Protocols (Revised)

**Issue:** casehubio/agency#360
**Date:** 2026-10-27
**Status:** Revised — supersedes tiered state machine approach

## Problem

Qhorus has four built-in channel protocols implemented as Java classes with hardcoded logic. Two of these — REQUEST_RESPONSE and TASK_COMPLETION — encode important communication patterns that consumers cannot easily inspect, modify, or extend.

## Goal

Express REQUEST_RESPONSE and TASK_COMPLETION as standard playbook YAML files — using the existing playbook executor with counters, guards, and pattern matching for exact behavioral equivalence.

## Key Design Decision

A protocol playbook is a normal playbook. Same YAML format, same compiler, same executor, same primitives. No separate protocol engine. No separate state map format.

The earlier approach (tiered state machine via `CompiledProtocol.from()`) was an approximation that lost precision — it couldn't count, couldn't guard, couldn't pattern-match. The standard playbook executor already has all of these. `PlaybookProtocolEvaluator` and its parallel engine are technical debt (platform#669 filed for cleanup).

## Design

### File Location

```
agency-core/src/main/resources/playbook-templates/
├── request-response.playbook.yaml
└── task-completion.playbook.yaml
```

### Playbook Format

Each protocol playbook uses the standard playbook YAML format:

- **Front-matter** — `playbook: "1.0"`, `name`, and `metadata` block
- **Metadata** — declares the playbook's role (`type: protocol`), pre-initializes resources (counter defaults, policyOverride keys for per-channel customisation), and documents exact behavioral rules
- **Body** — event-driven loop using `from:` + `loop: continuous`, `match` on event type, `counter-add`/`counter-get` for tracking, `when` guards for threshold checks, advisory emission

### Template: request-response.playbook.yaml

**Source protocol:** `RequestResponseProtocol` (qhorus runtime-core)

**Exact behavior to reproduce:**
1. When a QUERY arrives and open queries >= `maxOpenQueries` (default 3): emit WARNING
2. When a non-QUERY, non-RESPONSE message arrives with open queries > 0: emit ADVISORY/INFO

```yaml
playbook: "1.0"
name: request-response
---
metadata:
  type: protocol
  config:
    maxOpenQueries: { default: 3, override-key: request-response.max-open-queries }
  rules:
    - "QUERY with openQueries >= maxOpenQueries → WARNING/INVESTIGATE"
    - "non-QUERY non-RESPONSE with openQueries > 0 → INFO/LOG"
---
- step: evaluate
  from: dispatch-events
  loop: continuous
  match: "${event.type}"
  cases:
    - pattern: "QUERY"
      counter-add: { name: "openQueries", delta: 1 }
      when ${counter-get('openQueries') >= config.maxOpenQueries}:
        - advisory: { severity: WARNING, message: "unanswered queries exceed threshold", action: INVESTIGATE }
    - pattern: "RESPONSE"
      counter-add: { name: "openQueries", delta: -1 }
    - default:
        - when ${counter-get('openQueries') > 0}:
            - advisory: { severity: INFO, message: "channel has open queries awaiting response", action: LOG }
```

### Template: task-completion.playbook.yaml

**Source protocol:** `TaskCompletionProtocol` (qhorus runtime-core)

**Exact behavior to reproduce:**
1. When a COMMAND arrives and open commands >= `maxOpenCommands` (default 3): emit WARNING
2. When the sender is the obligor on an open command and incoming is not DONE/FAILURE/DECLINE: emit WARNING

```yaml
playbook: "1.0"
name: task-completion
---
metadata:
  type: protocol
  config:
    maxOpenCommands: { default: 3, override-key: task-completion.max-open-commands }
  rules:
    - "COMMAND with openCommands >= maxOpenCommands → WARNING/INVESTIGATE"
    - "obligor sends non-completion message with open obligation → WARNING/INVESTIGATE"
---
- step: evaluate
  from: dispatch-events
  loop: continuous
  match: "${event.type}"
  cases:
    - pattern: "COMMAND"
      counter-add: { name: "openCommands", delta: 1 }
      when ${counter-get('openCommands') >= config.maxOpenCommands}:
        - advisory: { severity: WARNING, message: "incomplete tasks exceed threshold", action: INVESTIGATE }
    - pattern: "DONE"
      counter-add: { name: "openCommands", delta: -1 }
    - pattern: "FAILURE"
      counter-add: { name: "openCommands", delta: -1 }
    - pattern: "DECLINE"
      counter-add: { name: "openCommands", delta: -1 }
    - default:
        - when ${event.sender == obligor && counter-get('openCommands') > 0}:
            - advisory: { severity: WARNING, message: "obligor has an open obligation", action: INVESTIGATE }
```

Note: the obligor check requires sender context from the dispatch event. The `event.sender == obligor` guard assumes the dispatch binding provides obligor identity. If this isn't available in the event payload, the obligor rule is documented in metadata but not enforced by the playbook — same gap as before, but explicit.

### Tests

Add a test-scoped dependency on platform's playbook executor modules. Tests:

1. **Parse validation** — each playbook parses through the playbook walker (same pattern as `PlaybookParsingTest`)
2. **Execution validation** — feed simulated events, assert counter state and advisory output:
   - REQUEST_RESPONSE: feed 3 QUERYs, verify WARNING on 3rd. Feed RESPONSE, verify counter decrements. Feed non-QUERY with open queries, verify INFO.
   - TASK_COMPLETION: feed 3 COMMANDs, verify WARNING on 3rd. Feed DONE, verify decrement. Feed FAILURE/DECLINE, verify decrement.

### Dependencies

Add to agency-core's `pom.xml` at test scope:
- `casehub-platform-yaml-core` — OrcCounter, OrcStateMachine, ExecutionScope
- `casehub-platform-yaml-step-runtime` — playbook parser, walker, step catalog

### Documentation

Update `docs/guides/consumer-guide.md` Protocol Templates section:
- Protocol playbooks are standard playbooks — same format, same executor
- Front-matter metadata declares role and pre-initializes resources
- How to fork and customize (change thresholds, add cases, modify advisories)

### What's Not In Scope

- No `ChannelProtocol` adapter bridge (qhorus#497)
- No CONTRIBUTION_REQUIRED or ROUND_ROBIN playbooks
- No deletion of `PlaybookProtocolEvaluator` (platform#669)

## Behavioral Equivalence

| Aspect | Java Implementation | Playbook |
|--------|-------------------|----------|
| Commitment counting | Exact count via activeCommitments | Counter primitive — exact |
| Configurable threshold | QhorusConfig + policyOverrides | metadata.config with override-key |
| Obligor check | Per-sender obligation tracking | Guard on event.sender (if available in payload) |
| Advisory emission | DispatchAdvisory | advisory step output |

The playbooks achieve exact behavioral equivalence for counting and threshold logic. The obligor check depends on dispatch binding providing sender context.

## References

- `RequestResponseProtocol.java` (qhorus runtime-core)
- `TaskCompletionProtocol.java` (qhorus runtime-core)
- `counter.playbook.yaml` (platform playbooks/coordination-primitives)
- `match-cases.playbook.yaml` (platform playbooks/step-workflows)
- `when-guard.playbook.yaml` (platform playbooks/flow-control)
- `from-channel-close.playbook.yaml` (platform playbooks/flow-control)
- `OrcCounter.java` (platform yaml-core)
- `PlaybookParsingTest.java` (platform yaml-step-runtime)
- casehubio/platform#661 — playbook-as-protocol bridge (closed)
- casehubio/platform#669 — delete PlaybookProtocolEvaluator (filed)
- casehubio/qhorus#497 — playbook protocol lifecycle (open)
