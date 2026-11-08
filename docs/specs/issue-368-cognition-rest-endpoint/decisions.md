# Decisions — issue-368-cognition-rest-endpoint

## D1: Endpoint location

**Choice:** Agency deployment module (agentic-yaml-deployment)
**Alternatives:**
- Agency runtime module — plain JAX-RS resource, consumers opt in via Jandex. Less automatic.
- Consumer apps — agency provides CDI bean only, each consumer writes endpoint. Duplicated boilerplate.
**Rationale:** Matches existing CognitionConfigBuildItem pattern. BuildStep + Recorder auto-registers endpoint for all consumers on the classpath.
**Trade-offs:** Consumers get the endpoint whether or not they want it (mitigated by it being a simple GET).
**Sources:** `agentic-yaml-deployment/AgenticYamlProcessor.java`, `CognitionConfigBuildItem.java`
**Exploration:** quick
**Status:** captured

## D2: Field naming mismatch

**Choice:** `@JsonProperty("personalityEvolution")` on the Java `personality` field
**Alternatives:**
- Rename Java field to `personalityEvolution` — breaking change to YAML consumers and CognitionCompiler.
- Rename frontend type to use `personality` — breaking change to casehub-mosaic.
**Rationale:** Non-breaking. YAML deserialization still uses `personality`, REST output uses `personalityEvolution` matching the frontend type.
**Trade-offs:** Slight asymmetry between YAML input name and JSON output name.
**Sources:** `CognitionDefinition.java:8`, mosaic-core `social.ts:152`
**Exploration:** quick
**Status:** captured

## D3: Endpoint scope

**Choice:** All specs — serve cognition + patterns + prompt optimisation + world from a single `/api/agency/specs` endpoint
**Alternatives:**
- Cognition only — matches issue scope exactly but leaves the frontend data gap for other types.
**Rationale:** All four build items already exist. Serving them together gives the frontend a single fetch for all agentic YAML specs. Payload is small (static config loaded at startup).
**Trade-offs:** Returns all specs even if frontend only needs one piece. Acceptable for small, static data.
**Sources:** `AgenticPatternsBuildItem.java`, `CognitionConfigBuildItem.java`, `PromptOptimisationBuildItem.java`, `WorldConfigBuildItem.java`
**Exploration:** quick
**Depends on:** D1 (endpoint lives in deployment module)
**Status:** captured

## D4: Endpoint design

**Choice:** Single aggregate GET endpoint returning a JSON object with named fields per spec type
**Alternatives:**
- Per-type endpoints (`/api/agency/specs/cognition`, `/patterns`, etc.) — multiple fetches, more boilerplate.
- Query parameter filtering (`?include=cognition,patterns`) — unnecessary complexity for small static data.
**Rationale:** Simplest for frontend. Single fetch, total payload is a few KB. Frontend reads the field it needs and ignores the rest.
**Trade-offs:** No selective fetching, but payload is trivially small.
**Sources:** agent-viewer `agent-viewer.ts` (presentational component, receives data via property binding)
**Exploration:** quick
**Depends on:** D3 (all specs in scope)
**Status:** captured
