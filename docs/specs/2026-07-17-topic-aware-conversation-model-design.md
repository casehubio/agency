# Topic-Aware Conversation Model

How the conversation projection becomes a structured workflow visualisation
surface — topic-grouped, obligation-aware, reaction-decorated — and why
this is fundamentally different from what everyone else is building.

**Issue:** casehubio/agency#49
**Related:** casehubio/work#237 (structured progress), casehubio/engine#84 (milestone/stage/goal alignment), blocks#62 (progress integration — deferred)

---

## The Problem Everyone Else Is Solving Wrong

The industry standard for agentic conversation memory is "persist the chat,
replay it into context." That's a distributed append-only log with no read
model. The cost is O(history) on every read, and every agent pays it.

What CaseHub builds instead is CQRS applied to agentic conversations. The
projection is a materialised view over the event stream — bounded by active
state, not history length. A channel with 2,000 messages and 3 open points
costs the same as a channel with 20 messages and 3 open points. The history
is still there (qhorus stores it), but agents don't read it — they read the
projection.

Nobody in the LLM tooling space is doing this. They're all building better
chat logs.

### Why structure beats raw replay

**Cost compounds, structure doesn't.** A 200-message channel costs ~50K
tokens every time an LLM reads it. A structured projection is ~2K. Multiply
by every agent in the choreography — five agents across seven steps means
the flat transcript is re-parsed on every turn. With projections, each agent
gets the current state. The cost difference is multiplicative: more agents ×
more steps × more messages. And projections support incremental folding from
a cursor — new messages don't re-process history.

**Determinism, not capability.** An LLM CAN reconstruct structure from a flat
transcript — usually. But "usually" isn't acceptable for the obligation chain
in an AML case or a clinical review. Did step 4 complete or was it a STATUS
that never reached DONE? The fold gets it right 100% of the time — same input,
same output, no inference. When five agents independently infer structure from
the same transcript, they can disagree about what step they're on. The
projection is the single source of truth.

**Composability at join time.** When a sub-agent spins up at step 5, hand it
the projection: "you're in topic risk-assessment, 2 open points relevant to
you." That's a prompt preamble. The alternative is "here are 200 messages,
figure out where you are" — the agent's first turn is wasted on orientation,
and it might orient wrong.

### What this enables for CaseHub consumers

**Clinical case review.** Three topics: patient-history, treatment-plan,
insurance-pre-auth. The case worker sees topic-grouped points with a progress
overlay — not 47 interleaved messages. The obligation chain proves each step
was commanded, progressed, and completed (audit trail).

**AML transaction monitoring.** Topics: transaction-analysis, kyc-verification,
suspicious-activity-report. The obligation chain (COMMAND → STATUS → DONE)
is compliance evidence. Reactions show the compliance officer acknowledged
findings without writing full responses.

**DevTown code review.** Topics map to review dimensions: correctness,
security, performance. Each topic has its own points and responses. The
supervisor sees overall review progress across dimensions.

**Drafthouse debates.** Already the primary consumer — topic grouping
organises multi-round deliberation. Reactions provide lightweight
acknowledgment without cluttering the conversation thread.

---

## Current State

### What exists

| Component | What it does | Topic-aware? |
|-----------|-------------|-------------|
| `MessageView.topic()` | Carries topic name (String) on every message | ✅ qhorus built |
| `MessageDispatch.topic()` | Sets topic on outbound messages (defaults to "general") | ✅ qhorus built |
| `MessageQuery.topic()` | Filters messages by topic in store queries | ✅ qhorus built |
| `ProjectionService.project(channelId, scope, projection)` | Folds messages matching a `MessageQuery` scope | ✅ qhorus built |
| `Topic` record | Full lifecycle: create, resolve, rename, merge, move | ✅ qhorus built |
| `ReactionService.getReactionsBatch()` | Batch-fetch `Map<Long, List<ReactionGroup>>` by message IDs | ✅ qhorus built |
| `ConversationProjection` | Folds `MessageView` → `ConversationState` | ❌ ignores topic |
| `ConversationState` | Flat map of points, no topic grouping | ❌ no topic |
| `ConversationPoint` | id, classification, thread, status | ❌ no topic |
| `ThreadEntry` | entryId, role, round, entryType, content | ❌ no messageId, no messageType |
| `ConversationRenderer` | Groups by status (unresolved/escalated/resolved) | ❌ no topic grouping |
| `ChannelAgentRequest` | channelId, correlationId, message (OutboundMessage) | ❌ no topic |

### The gap

The qhorus infrastructure is fully topic-aware. The blocks conversation model
ignores topics entirely. Every field needed to bridge them already exists on
`MessageView` — `topic()`, `id()`, `type()` — but the projection discards
them during the fold.

---

## Design

### Principle: enrich the fold, keep the SPI

The `ChannelProjection<S>` SPI doesn't change. Topic-scoped projection is
already a query concern — `ProjectionService.project(channelId,
MessageQuery.builder().topic("review").build(), projection)` feeds only
matching messages to the fold. The projection folds whatever it's given.

The changes are all additive: new fields on existing records, one new
renderer signature, richer config. No existing behaviour changes. All
existing tests pass unchanged.

### 1. Record changes

**ConversationPoint** — add `String topic`

```java
public record ConversationPoint(
        String id,
        String topic,
        PointClassification classification,
        List<ThreadEntry> thread,
        String status) {
    public ConversationPoint {
        thread = List.copyOf(thread);
    }
}
```

Populated from `MessageView.topic()` at point initiation — always non-null
(qhorus defaults to `"general"` when no topic is set). Responses to a point
inherit the initiating point's topic (same correlationId chain, same topic).

**ThreadEntry** — add `Long messageId`, `MessageType messageType`

```java
public record ThreadEntry(
        String entryId,
        Long messageId,
        MessageType messageType,
        String role,
        int round,
        String entryType,
        String content) {}
```

`messageId` enables reaction correlation — currently discarded, an oversight
worth fixing. `messageType` enables obligation chain rendering (COMMAND →
STATUS → DONE). blocks already depends on qhorus-api at compile scope.

**ConversationState** — add progress hook (deferred population)

```java
public record ConversationState(
        Map<String, ConversationPoint> points,
        List<FlagEntry> humanFlags,
        List<RoundMemo> memos,
        Map<String, SubTaskFinding> subTaskFindings) { ... }
```

No `ChoreographyProgress` field. Progress rendering is deferred to blocks#62,
which integrates work#237's `ProgressInstance` when it lands. The state record
stays unchanged for now — progress will be a supplementary render-time input
(same pattern as reactions).

**ChannelAgentRequest** — add `String topic`

```java
public record ChannelAgentRequest(
        UUID channelId,
        String correlationId,
        OutboundMessage message,
        String topic) {}
```

Dispatch handlers know the topic of the triggering message and can respond
in the right place.

### 2. Projection changes

**ConversationProjection.apply()** — three additions to `doApply`:

1. Pass `message.topic()` through to `ConversationFold.createPoint()` at
   point initiation
2. Pass `message.id()` and `message.type()` through to all
   `ConversationFold` methods that create `ThreadEntry` instances
3. No choreography metadata parsing — deferred to blocks#62

The three abstract hook methods (`sentinel()`, `isPointInitiator()`,
`statusAfter()`) don't change. Subclasses are unaffected.

### 3. Fold changes

**ConversationFold** — updated signatures:

- `createPoint()` — takes `topic`, `messageId`, `messageType`; stores topic
  on point, messageId/messageType on first thread entry
- `respondToPoint()` — takes `messageId`, `messageType`; stores on new
  thread entry
- `flagHuman()`, `addMemo()` — takes `messageId`
- Infrastructure handlers (SUB_TASK_REQUEST, SUB_TASK_FINDING, SUB_TASK_ERROR)
  — takes `messageId`

All changes are additive parameters. Existing fold logic is unchanged.

### 4. Renderer changes

**ConversationRenderer.render()** — new overload:

```java
public String render(ConversationState state,
                     Map<Long, List<ReactionGroup>> reactions)
```

The existing `render(ConversationState)` stays as a convenience overload
(passes empty reactions map).

**Rendering logic:**

1. **Topic grouping** — when `config.groupByTopic()` is true, group points
   by `point.topic()`, render each topic as a section header. Within each
   topic, the existing status grouping (unresolved/escalated/resolved)
   applies.

2. **Obligation chain** — within each topic section, show the `MessageType`
   progression as a compact line: `COMMAND → STATUS → STATUS → DONE ✓` or
   `COMMAND → STATUS → ⏳` (incomplete). Derived from `ThreadEntry.messageType()`
   values within the topic.

3. **Reactions** — after each thread entry, look up
   `reactions.get(entry.messageId())` and render emoji counts inline
   (e.g., `👍×3 ✅×1`). Null or empty → no decoration.

4. **Progress overlay** — placeholder. When blocks#62 integrates work#237,
   the renderer will accept a progress snapshot and render it at the top of
   each topic section. For now, no progress display.

**ConversationRendererConfig** — new fields:

```java
Map<MessageType, String> messageTypeLabel,
boolean groupByTopic,
boolean showObligationChain
```

`messageTypeLabel` maps `MessageType.COMMAND` → "commanded", etc. for
readable obligation chain rendering. `groupByTopic` toggles topic sections
vs flat (backward compatible — defaults to false). `showObligationChain`
toggles the COMMAND→STATUS→DONE line.

### 5. Two usage modes

**Full-channel projection** (multi-topic view):
```java
var state = projectionService.project(channelId, projection).state();
var messageIds = collectMessageIds(state);
var reactions = reactionService.getReactionsBatch(messageIds);
var markdown = renderer.render(state, reactions);
```
All messages, all topics. Points carry their topic. Renderer groups by topic.

**Topic-scoped projection** (single-topic view):
```java
var scope = MessageQuery.builder().topic("review").build();
var state = projectionService.project(channelId, scope, projection).state();
var markdown = renderer.render(state);
```
Only messages from "review" topic. All points have the same topic. Renderer
can skip topic headers (single topic = no grouping needed).

No `ConversationProjection` code difference — the filtering happens at the
query layer, which qhorus already provides.

### 6. What doesn't change

- `ChannelProjection<S>` SPI — untouched
- `ProjectionService` — already supports topic-scoped queries
- `ConversationProjection` class structure — still abstract, still three hooks
- `ConversationProtocol` — no new metadata keys (progress deferred to blocks#62)
- `ChannelAgentDispatcher` — passes topic from request to handler, no structural change

---

## Deferred: structured progress integration (blocks#62)

The choreography progress overlay ("step 3 of 7: calibration") is deferred
to blocks#62, which depends on:

- **casehubio/work#237** — structured progress as a platform primitive:
  schema-validated, hierarchical `ProgressInstance` tree. Progress is not a
  field on a WorkItem; it is a first-class entity — scoped, tree-structured,
  separately tracked from task lifecycle.
- **casehubio/engine#84** — milestone/stage/goal alignment. work#237
  extends/replaces milestones; `ProgressInstance` is a superset of the
  binary milestone concept.

When work#237 delivers, blocks#62 integrates `ProgressUpdatedEvent` as a
supplementary render-time input (same pattern as reactions). The renderer
shows the progress tree as a choreography overlay per topic.

This issue (blocks#49) builds the rendering surface that blocks#62 plugs
into. Topic grouping, message identity, obligation chains, and reactions
are the foundation. Progress is the overlay.

---

## Test Strategy

### What the tests prove

The issue's raison d'être: prove that engine choreography → qhorus topic
dispatch → conversation projection → conversation renderer composes
correctly end-to-end. Each repo tests its own contribution; blocks tests
that all three compose.

### Test structure

Tests are plain JUnit 5 with Mockito (no CDI, no Quarkus runtime — matching
blocks' existing test infrastructure).

**Test 1: Topic-scoped projection.** Fold messages across multiple topics.
Assert points are attributed to the correct topic. Assert topic-scoped
`MessageQuery` filtering produces a single-topic view.

**Test 2: Topic-grouped rendering.** Build a `ConversationState` with points
in three topics. Render with `groupByTopic=true`. Assert section headers,
point grouping, status ordering within each topic.

**Test 3: Obligation chain rendering.** Build thread entries with
`MessageType` progression (COMMAND → STATUS → DONE). Render with
`showObligationChain=true`. Assert the compact obligation line appears per
topic.

**Test 4: Reaction decoration.** Build a `ConversationState` with known
`messageId` values on thread entries. Pass a reactions map. Assert emoji
counts appear inline after the correct entries.

**Test 5: ChannelAgentDispatcher topic passthrough.** Construct a
`ChannelAgentRequest` with topic. Assert the handler receives the topic
and can set it on the `MessageDispatch` response.

**Test 6: Full end-to-end composition.** Simulate a multi-step workflow:
- Choreography dispatches messages to three topics (review, analysis, approval)
- Each topic has COMMAND → STATUS → DONE obligation chain
- Points are raised and responded to within topics
- Reactions are attached to specific messages
- Assert: rendered output matches the workflow definition — correct topic
  grouping, obligation chains complete, reactions decorated, points in the
  right topics with the right statuses

### Test helper: TestConversationProjection

The existing `TestConversationProjection` (inner class of
`ConversationProjectionTest`) provides a concrete subclass with `sentinel()`,
`isPointInitiator()`, and `statusAfter()` implementations. Reuse it for
integration tests.

### Test helper: message builders

Extend `TestMessages` to support topic:

```java
public static MessageReceivedEvent received(UUID channelId, MessageType type,
                                            String content, String correlationId,
                                            String sender, String topic) { ... }
```

Build `MessageView` mocks with `id()`, `type()`, and `topic()` returning
controlled values (currently tests only mock `content()` and `correlationId()`).

---

## Impact on consumers

All changes are additive. Existing consumers are unaffected:

| Consumer | Impact |
|----------|--------|
| drafthouse | `DebateChannelProjection` extends `ConversationProjection` — gains topic-awareness automatically. `ChannelAgentDispatcher` subclass needs to pass topic through `ChannelAgentRequest`. |
| devtown | Same as drafthouse — conversation projection subclass gains topic for free. |
| clinical, AML | Use oversight blocks, not conversation — unaffected. |

The `ConversationRendererConfig.groupByTopic` default is `false`, so
existing rendering is unchanged until consumers opt in.

---

## Dependencies

All compile-scope dependencies already exist in blocks' pom.xml:

- `casehub-qhorus-api` — `MessageView`, `MessageType`, `ReactionGroup`
- `casehub-engine-api` — (no new types needed for this issue)
- blocks' own conversation package — all changes are internal

No new dependencies. No qhorus changes needed. No engine changes needed.
