# Decisions — #526 Zero-Java Avatar Self-Wiring

## D1: Module extraction strategy

**Choice:** New `speech-deployment` module — dedicated Quarkus deployment module with a narrow dependency set
**Alternatives:**
- Sub-module restructure — keep in agentic-yaml-deployment but break the agency-core dependency. Risk of re-introducing the dependency later.
**Rationale:** Clean module boundary. Consumer adds speech-deployment + speech-ws + agent-claude. No agency-core on classpath at all.
**Trade-offs:** New module to maintain. Build matrix grows by one.
**Sources:** agentic-yaml-deployment/pom.xml, speech-demo/pom.xml (exclusion pattern)
**Exploration:** quick
**Status:** captured

## D2: Avatar type location

**Choice:** Stay in agentic-yaml, speech-deployment depends on agentic-yaml with `<exclusion>` for agency-core
**Alternatives:**
- Move to speech-api or new speech-runtime — cleanest graph but breaks existing consumers
- Copy into speech-deployment — duplicates create maintenance burden
**Rationale:** Proven pattern — speech-demo already uses this exclusion successfully. No type relocation, no consumer breakage.
**Trade-offs:** Maven exclusions are fragile if someone adds a new transitive path to agency-core later.
**Sources:** speech-demo/pom.xml lines 25-33 (existing exclusion), AvatarCompiler.java imports (no agency-core references)
**Exploration:** quick
**Status:** captured

## D2b: Infrastructure class relocation

**Choice:** Move `VoicesEndpoint`, `JsonPerformanceVocabulary`, `DiagnosticEndpoint` into `speech-ws`. `ModelProvisioningService` + `ModelStatusEndpoint` stay in a sherpa-aware module (alongside AvatarCompiler infrastructure).
**Alternatives:**
- All into speech-ws — adds connectors-voice-sherpa dependency to speech-ws
- New speech-runtime module — clean separation but one more module
**Rationale:** speech-ws already depends on speech-api and platform-agent-api, which is all the first three classes need. ModelProvisioningService requires sherpa native classes and belongs with the compilation infrastructure.
**Trade-offs:** ModelProvisioningService placement still TBD — either agentic-yaml or the new module.
**Depends on:** D1 (new speech-deployment module), D2 (types stay in agentic-yaml)
**Sources:** speech-demo Java files, speech-ws/pom.xml dependency set
**Exploration:** quick
**Status:** captured

## D3: LLM provider integration (eidos + agent-config)

**Choice:** Compose three platform layers: (1) eidos descriptor declares the avatar agent identity — briefing (system prompt), model tier, capabilities, provider — in `META-INF/eidos/descriptors.yaml`; (2) agent-config.yaml provides credentials and default backend via layered lookup; (3) `application.properties` sets `casehub.avatar.agent-id` to wire the descriptor. `EidosAgentResolver` resolves system prompt + model from the descriptor. `ClaudeAgentProvider` (from `agent-claude`) handles invocation. `SpeechWebSocket` already has `AvatarConfig.agentId()` and `AvatarConfig.tenantId()` — the wiring point exists.
**Alternatives:**
- Raw application.properties for model/credentials — bypasses eidos, doesn't use platform's existing resolution
- AgentProvider as injected SPI — requires consumer Java code
- Built-in Vertex AI with config — locks to one provider, ignores platform infrastructure
**Rationale:** Zero Java. Uses the established platform stack: eidos for agent identity/model selection, agent-config for credentials, agent-claude for invocation. The avatar's system prompt, model tier, and capabilities are declarative YAML — no hardcoded model IDs.
**Trade-offs:** Consumer must understand three YAML files (descriptors.yaml, agent-config.yaml, avatar.yaml). But each file serves one purpose and the platform already documents them.
**Sources:** eidos/api/AssembledPrompt.java (already imported in SpeechWebSocket), eidos/runtime/resolver/EidosAgentResolver.java (AgentResolver impl), speech-ws/AvatarConfig.java lines 19-21 (agentId/tenantId already declared), platform/agent-claude/ClaudeBeans.java, platform/agent-config-core/AgentConfigLoader.java, eidos/examples/agent-scenarios/descriptors.yaml (descriptor format)
**Exploration:** quick
**Status:** captured

## D4: Frontend component packaging

**Choice:** ES module bundle (`avatar-component.mjs`) with integrated controls (voice selector, LLM picker, speed slider, animation picker)
**Alternatives:**
- Web Component (Custom Element) — strongest encapsulation but shadow DOM constraints and boilerplate
- Template HTML — simplest but not reusable
**Rationale:** Matches zero-code goal. Consumer imports one module, calls one init function. Controls included by default.
**Trade-offs:** Styling is limited to what the component exposes. Consumer-specific UI requires overriding the built-in controls.
**Sources:** speech-demo/src/main/resources/META-INF/resources/index.html (750 lines of inline JS)
**Exploration:** quick
**Status:** captured
