## D1: Bridge cardinality — one coordinator per deployment

**Choice:** Singleton CDI bean with programmatic `bind(SwarmEventSource)`
**Alternatives:**
- Per-case registry (`ConcurrentMap<UUID, SwarmEventSource>`) — overengineered for current usage; no deployment runs multiple coordinators
**Rationale:** Current `SwarmCoordinatorBuilder` usage is one-per-deployment. Simpler design, no registry overhead.
**Trade-offs:** If multi-coordinator support is needed later, the bridge must be refactored to a registry pattern.
**Sources:** `SwarmCoordinatorBuilder.java`, `RuntimeBeans.java` (CDI wiring)
**Exploration:** quick
**Status:** captured

## D2: SwarmInitialised experiences mismatch

**Choice:** Pass `List.of()` for experiences in the bridge mapping
**Alternatives:**
- Add experiences to engine SPI — cross-repo change, experiences are agency-level concern not engine's
- Remove experiences from `SwarmCoordinationEvent.SwarmInitialised` — breaking change to agency event model
**Rationale:** Experiences are loaded separately by the coordinator. The engine SPI correctly doesn't know about CBR retrieval. Empty list is safe — `SwarmContext.onSwarmInitialised()` handles it, and the coordinator populates experiences through its own path.
**Trade-offs:** Engine-originated `SwarmInitialised` events won't carry experiences. Coordinator must populate them separately.
**Sources:** `SwarmCoordinationListener.java:32`, `SwarmCoordinationEvent.java:11`, `SwarmContext.java:19-22`
**Exploration:** quick
**Status:** captured

## D3: Binding mechanism — programmatic bind()

**Choice:** `@ApplicationScoped` bridge with `bind(SwarmEventSource)` method; events before binding are no-ops
**Alternatives:**
- Shared CDI holder/registry — adds a type for no benefit over direct binding
- Make `SwarmEventSource` CDI-managed — changes builder internals, couples to CDI
**Rationale:** Matches existing agency patterns (CDI beans that receive configuration programmatically). No new types. Explicit, simple. Silent no-op before binding is correct — no coordinator exists to receive events yet.
**Trade-offs:** Consumer must remember to call `bind()` after building the coordinator. No compile-time enforcement.
**Sources:** `SwarmCoordinatorBuilder.java:82-113`, `LlmSwarmCoordinator.java:57`
**Exploration:** quick
**Status:** captured
