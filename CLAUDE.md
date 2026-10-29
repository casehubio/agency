# CLAUDE.md

**Name:** casehub-agency

# CaseHub Agency

## Project Type

type: java

## Repository Role

Reusable building blocks for CaseHub applications — composed from qhorus, engine, and work primitives. Foundation-adjacent library (sits between foundation and application tier). Single module, single artifact: `casehub-agency`.

**Peer repos (each has its own Claude session — do not commit to these):**
platform, eidos, ledger, connectors, iot, work, worker, qhorus, pages, engine, claudony, openclaw, neural-text, devtown, aml, clinical, drafthouse, life, quarkmind, flow, soc, fsitrading, ras, ops, workers, desiredstate

## Platform Docs
- [Platform Index](https://raw.githubusercontent.com/casehubio/parent/main/docs/INDEX.md) — discovery index (start here)
- [Building Platform](https://raw.githubusercontent.com/casehubio/parent/main/docs/guides/building-platform.md) — platform contributor guide

## Repo Guide

This repo owns its own documentation, synced to parent via CI:
- `docs/guides/consumer-guide.md` — for app builders: modules, APIs, quick start
- `docs/guides/contributor-guide.md` — for platform builders: architecture, SPIs, internals

Update the relevant guide in the same session when implementation changes modules, SPIs, or public APIs. Do not defer — drift compounds.

Read `docs/guides/consumer-guide.md` for app-level work. Only read `docs/guides/contributor-guide.md` when modifying this repo's internals or extension points.

## Build Commands

```bash
mvn --batch-mode install
mvn --batch-mode test
mvn --batch-mode test -pl agentic-yaml -Dschema.update    # regenerate PatternSpec schema baseline
```

## Testing

No Quarkus runtime — plain JUnit 5 tests with Mockito. No CDI container in tests.

## Key Directories

| Path | Contents |
|------|----------|
| `summarisation-api/src/main/java/io/casehub/agency/summarisation/` | Unified summarisation SPI — `Summariser<IN,OUT>`, `StatefulSummariser<IN,OUT,S>` (framework-managed state per partition), `ContentSummariser<T,R>` (with `asSummariser()` bridge to `StatefulSummariser`), `LevelEvent<E>` (with `@Nullable String tenancyId`), `EventStreamBus<E>`, `EventAccumulator<E>` (with `peekBuffer()`), `WindowPolicy`, `EmissionPolicy<IN,S>` (state-aware emission gating with `anyOf`/`allOf` composition, `window(WindowPolicy)` factory), `StateStore<S>` (opt-in persistent state — `load`/`store` with write-through cache in runners), `OutputProcessor<OUT,S>` (post-summarisation output processing), `SummarisationRunner<IN,OUT>` (state-aware, builder API with `emissionPolicy()`/`stateStore()`/`stateKeyResolver()`/`outputProcessor()`), `KeyedAccumulator<K,E>` (key extractor: `Function<LevelEvent<E>, K>`), `KeyedSummarisationRunner<K,IN,OUT>` (per-key `StatefulSummariser` state + `evictState(K)`, builder API with `stateStore()`/`outputProcessor()`), `Compactor<E>`, `VerbatimContentSummariser<T>`, `Tickable` (common `tick()`/`flush()` interface for both runner types) |
| `summarisation-api/src/test/java/io/casehub/agency/summarisation/` | Tests for summarisation SPI |
| `cloudevents/src/main/java/io/casehub/agency/summarisation/cloudevents/` | CloudEvent bridge — `EventSink<T>` (`@FunctionalInterface`), `CloudEventIngestionAdapter<E>` (type-prefix filter, tenancyId extraction, optional `Function<E,E>` payload normalizer for mixed-type pipelines), `CloudEventEmitter<E>` (wraps `LevelEvent` as CloudEvent), `PipelineTickScheduler` (ScheduledExecutorService tick loop) |
| `cloudevents/src/test/java/io/casehub/agency/summarisation/cloudevents/` | Tests for CloudEvent bridge |
| `summarisation-yaml/src/main/java/io/casehub/agency/summarisation/yaml/` | YAML pipeline surface — `PipelineDefinition`, `PipelineCompiler` (windowed + keyed grouping via `ExpressionEngine`), `CompiledPipeline<IN>` (polymorphic `Tickable` runners), `SummariserRegistry`, `PipelineValidator`, `ExpressionEngine`-backed expression evaluation, Jackson YAML deserialization |
| `summarisation-yaml/src/main/java/io/casehub/agency/summarisation/yaml/builtin/` | Built-in summariser types — `ThresholdClassifySummariser` (per-event MVEL3 predicates), `PhaseDetectSummariser` (stateful state machine with structured predicates), `CountSummariser`, `FieldExtractSummariser`, `BatchContext`/`BatchContextView` |
| `summarisation-yaml/src/test/java/io/casehub/agency/summarisation/yaml/` | Tests for YAML surface |
| `summarisation-yaml-deployment/src/main/java/io/casehub/agency/summarisation/yaml/deployment/` | Quarkus deployment module — `SummarisationYamlProcessor` (YAML discovery, validation), `SummarisationRecorder` (runtime bean registration) |
| `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/` | YAML surface for agentic orchestration — `PatternDefinition` (8 topologies, each with optional `name`, `description`, `task`), `RoutingSpec`, `TerminationSpec`, `AggregationSpec`, `ActivationSpec`, `DecompositionSpec`, `AgentRefSpec` sealed record hierarchies, `BranchSpec` (condition + agent + optional `description`), `AgentRefSpec` variants now include optional `model` (LLM model identifier), `TaskNodeSpec` (Primitive with optional `description`, Compound with subtasks), `PatternCompiler` (spec → `ExecutionModel`), strategy registries, `AgencySchemaGenerator` (victools schema from spec records), `CapabilityCatalogGenerator` (JSON taxonomy catalog — all CapabilityType entries with category and hasYamlRoot flag), `JudgmentSpec`, `NegotiationDefinition`, `ConversationDefinition`, `ConflictResolutionSpec` |
| `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/` | Tests for YAML agentic surface |
| `agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/` | Quarkus deployment module — `AgenticYamlProcessor` (YAML discovery, validation), `AgenticRecorder` (runtime compiler registration) |
| `agency-core/src/main/resources/playbook-templates/` | Protocol playbooks — standard playbook YAML for REQUEST_RESPONSE, TASK_COMPLETION, CONTRIBUTION_REQUIRED, and ROUND_ROBIN with counter-based exact tracking and sender state tracking |
| `agency-core/src/main/resources/protocol-templates/` | Protocol state machine templates — YAML state maps for `CompiledProtocol.from()` (distinct from `playbook: "1.0"` step format in `playbook-templates/`). `negotiation.protocol.yaml` (MONITORING/PROPOSED/RESPONDING phases) |
| `docs/summarisation/CAPABILITY-MATRIX.md` | Summarisation capability matrix — maps 29 capabilities across 11 examples |
| `agency-core/src/main/java/io/casehub/agency/core/invoke/` | Shared LLM invocation utility — `StructuredAgentInvoker` (static: `invokeText()`, `invoke(Class<T>)`, `invokeRaw()` with sealed `InvocationResult<T>` and `InvocationMetadata`; markdown fence stripping, JSON extraction, `InvocationComplete` capture). `stripFences()` and `extractJson()` also available standalone. |
| `agency/src/test/java/io/casehub/agency/core/invoke/` | Tests for agent utility |
| `agency-core/src/main/java/io/casehub/agency/governance/attestation/` | Attestation write-path types — `AttestationIntent`, `AttestationIntentWriter` (+ `NoOpAttestationIntentWriter` `@DefaultBean`), `LifecycleAttestationObserver<E>` SPI, `AttestationContext` |
| `agency/src/test/java/io/casehub/agency/governance/attestation/` | Tests for attestation types |
| `agency-core/src/main/java/io/casehub/agency/governance/trust/` | Trust-lifecycle SPIs — `IntakeClassifier<S>`, `VouchService` with pluggable `VouchConstraint` chain |
| `agency/src/test/java/io/casehub/agency/governance/trust/` | Tests for trust SPIs |
| `agency-core/src/main/java/io/casehub/agency/communication/dispatch/` | Channel dispatch utilities — message meta, context tracking, bounded projection |
| `agency/src/test/java/io/casehub/agency/communication/dispatch/` | Tests for channel dispatch utilities |
| `agency-core/src/main/java/io/casehub/agency/communication/channel/summary/` | Channel summary integration — `ContentSummariser<Message, SummaryResult>` implementations, `SummaryUpdateHook` adapter, `NoOpThreadSummaryStore` `@DefaultBean` |
| `agency/src/test/java/io/casehub/agency/communication/channel/summary/` | Tests for channel summary integration |
| `agency-core/src/main/java/io/casehub/agency/core/` | Compositional agentic orchestration — six SPIs (routing, decomposition, activation, aggregation, judgment, termination), execution drivers, pattern builders |
| `agency/src/test/java/io/casehub/agency/core/` | Tests for orchestration framework |
| `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/` | LLM-driven stigmergy interpretation — event-driven LLM layer alongside engine tick loop (D1). `LlmObservation`/`LlmObservationResponse` (LLM-facing JSON records), `LlmObservationResult`/`ActionResult`/`InterpretationResult` (engine-facing results with `InvocationMetadata`), `LlmRecommendation`/`LlmActionResponse` (LLM action records), `RecommendedAction` (wraps `RuleAction` with `justifyingPatternIds`), `LlmInvocationPolicy` (caller-supplied latency thresholds), `EnvironmentWriter` (engine write-back SPI), `ObservationHandler` (`@FunctionalInterface` pattern-matched handler), `ObservationHandlerContext` (handler context with accumulated observation storage + flush), `ObservationDispatcher` (builder API, topological sort via `derivedFrom`, handler precedence: on > onDerived > onUnhandled, cycle detection), `EnvironmentWriteBackHandlers` (convenience handlers), `ChangeGatedEmissionPolicy` (watched-key change detection composing with delegate `EmissionPolicy`), `InFlightGate` (concurrency guard wrapping `ContentSummariser` — suppresses emission while in-flight), `LlmResponseConverter` (LLM JSON → engine `Observation`/`RuleAction`), `LlmSummarisers` (factory: `observation()`/`combined()`/`action()` templates producing `ContentSummariser`), `InterpretationBuilder` (fluent builder for template composition) |
| `agency/src/test/java/io/casehub/agency/stigmergy/llm/` | Tests for LLM stigmergy interpretation |
| `agency-core/src/main/java/io/casehub/agency/deliberation/judgment/` | Judgment orchestration — `JudgmentPhase<T>` SPI (from engine#994), `JudgmentPolicy<T>` composition root (implements `JudgmentPhase`), `JudgmentTrigger<T>` (conditional yielding: `AlwaysYield`, `NeverYield`, `IterationBased`, `ConfidenceThreshold`), `CallerStrategy` sealed hierarchy (Single/FanOut/EscalationChain), `CallerRef`, `AgreementPolicy` with `ConsensusAgreement`, `JudgmentDispatcher` with `DirectJudgmentDispatcher`, `CompositeVerifier` (chains engine-api `JudgmentVerifier` with most-restrictive-wins policy), `NoOpVerifier`, `RetryPolicy`/`ExhaustionPolicy`/`VerifierFailurePolicy`, `JudgmentResponse`, `AgreementResult` |
| `agency/src/test/java/io/casehub/agency/deliberation/judgment/` | Tests for judgment orchestration |
| `agency-core/src/main/java/io/casehub/agency/communication/channel/` | Inter-agent channel setup + supervisor observation — `ChannelBinding`, `ChannelConfig`, `ChannelExecutionStrategy` (Conversation/FanIn/Barrier), `ChannelObserver<S>` (projection-based observation + EventSource) |
| `agency/src/test/java/io/casehub/agency/communication/channel/` | Tests for channel infrastructure |
| `agency-core/src/main/java/io/casehub/agency/orchestration/aggregation/` | Aggregation strategies including `AuctionAggregation` — English/Dutch iterative auction via `AggregationStrategy<AuctionState>` with `BidExtractor` SPI |
| `agency/src/test/java/io/casehub/agency/orchestration/aggregation/` | Tests for aggregation strategies |
| `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/` | LLM swarm coordination — `LlmSwarmCoordinator` (composition root), `SwarmRoutingStrategy` (LLM-driven team formation with pre-filter fallback), `CollectiveReasoningAggregation` (first LLM-powered `AggregationStrategy`, optional `SystemPromptCustomiser` for variant instruction delta), `SwarmConvergenceTermination` (three-outcome: converge/deadlock/continue), `SwarmExperienceSection` (CBR plan trace replay), `SwarmEventSource` (`EventSource` bridge for engine swarm events), `SwarmCoordinatorBuilder` (fluent builder with optional `promptCustomiser()`), `SwarmProvisioningAdvisorImpl` (engine `SwarmProvisioningAdvisor` SPI with LLM + heuristic fallback, optional `SystemPromptCustomiser`), `SwarmInvocationPolicy`/`SwarmInvocationTracker` (per-execution LLM cost budgets with provisioning reserve), `SwarmFallbackStrategy` (heuristic degradation), `SwarmContext` (mutable state with volatile fields), `SwarmPromptSection` (`@FunctionalInterface` for coordinator prompt composition), `SwarmCoordinationEvent` (sealed event hierarchy), `SwarmDegradedEvent` |
| `agency/src/test/java/io/casehub/agency/orchestration/swarm/` | Tests for LLM swarm coordination |
| `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/prompt/` | Swarm prompt optimisation — `SwarmPromptSignatures` (three `PromptSignature` constants: TEAM_FORMATION, COLLECTIVE_REASONING, PROVISIONING), `SwarmOptimisedFewShotSection` (`SwarmPromptSection` impl injecting curated few-shot examples from active variant), `SwarmOutcomeMetric` (swarm-specific `PromptQualityMetric` — CONVERGED/CONSENSUS=1.0, COMPLETED=0.8, CONTINUE=0.5, DEGRADED=0.25, DEADLOCK/FAILED=0.0), `SwarmOutcomeMapper` (normalises swarm convergence outcomes to `VariantOutcome` records for offline batch cycle) |
| `agency/src/test/java/io/casehub/agency/orchestration/swarm/prompt/` | Tests for swarm prompt optimisation |
| `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/` | Coalition formation — `CoalitionProposal`, `CoalitionEvaluator` SPI, `CapabilityCoverageEvaluator`, capability-based team assembly scoring |
| `agency/src/test/java/io/casehub/agency/deliberation/coalition/` | Tests for coalition formation |
| `agency-core/src/main/java/io/casehub/agency/deliberation/intention/` | Joint intentions — `JointIntention` lifecycle (form/activate/reconsider/drop/fulfill), `IntentionMonitor` SPI, `ReconsiderationSignal` |
| `agency/src/test/java/io/casehub/agency/deliberation/intention/` | Tests for joint intentions |
| `agency-core/src/main/java/io/casehub/agency/deliberation/belief/` | Formal belief revision — AGM-style `BeliefSet<T>` with expand/contract/revise, `ConsistencyChecker<T>` SPI, entrenchment ordering |
| `agency/src/test/java/io/casehub/agency/deliberation/belief/` | Tests for belief revision |
| `agency-core/src/main/java/io/casehub/agency/social/cognition/` | Cognition integration — `CognitionAvatarAdapter` (thin `AvatarCognition` bridge delegating to neocortex `CognitionCore`, maps speech lifecycle to cognitive tick/record/evaluate), `MemoryHygieneSpiAdapter` (bridges agency `MemoryHygieneOrchestrator` to neocortex SPI). All cognitive types, orchestrators, prompt sections, and config records now provided by `casehub-neocortex-cognition` module. |
| `agency/src/test/java/io/casehub/agency/social/cognition/` | Tests for cognition integration |
| `agency-core/src/main/java/io/casehub/agency/social/cognition/evolution/` | Cognitive evolution ticker — `CognitiveEvolutionAdapter` (composition root: drive-modulated `EvolutionTickBridge` invocation with circuit breaker → mood feedback), `EvolutionTickBridge` (`@FunctionalInterface`), `CognitiveEvolutionConfig` (curiosity thresholds), `CircuitBreakerMoodMapper` (TickTrace gate → MoodSignal.DirectShift) |
| `agency/src/test/java/io/casehub/agency/social/cognition/evolution/` | Tests for cognitive evolution ticker |
| `agency-core/src/main/java/io/casehub/agency/social/memory/` | Memory hygiene — `MemoryHygieneOrchestrator` (tick: importance scoring → eviction → consolidation; implements neocortex `io.casehub.neocortex.cognition.memory.MemoryHygieneOrchestrator` SPI), `MemoryHygieneScheduler` (maintain: tick + reflection + peer-linking + integrity), `ConfidenceScorer` SPI with `ArousalScorer`/`SurpriseScorer`/`CompositeConfidenceScorer`, `RetentionScore`/`RetentionConfig` (composite eviction scoring), `DefaultIntegrityChecker` (structural + semantic escalation), `ReflectionStore` (reflection persistence SPI), `ReflectionQueryStore` SPI extending `io.casehub.neocortex.memory.ReflectionQueryStore` with `NoOpReflectionQueryStore` `@DefaultBean`, `HygieneTick`/`MaintenanceTick` sealed outcomes, `HygieneEvent` observability. Note: `ReflectionEntry` and `KnowledgeGapSummary` types moved to neocortex `memory-api`. |
| `agency/src/test/java/io/casehub/agency/social/memory/` | Tests for memory hygiene |
| `agency-core/src/main/java/io/casehub/agency/social/memory/evolution/` | Memory hygiene evolution loop — `MemoryHygieneOutcomeProvider` (`@FunctionalInterface`), `MemoryHygieneOutcome` (quality metrics record), `MemoryHygieneProposalSource` (`ImprovementProposalSource` — composite quality scoring with weakest-dimension routing), `MemoryHygieneRegressionEvaluator` (`RegressionEvaluator` — `memory-hygiene:*` component score monitoring), `MemoryHygieneCategoryProvider` (`ImprovementCategoryProvider` — evaluate/tune/verify stages), `MemoryHygieneConflictStrategy` (`ConflictStrategy` — per-agent mutex), `MemoryHygieneCapabilityArea` (`CapabilityArea` — health scoring with landscape position mapping) |
| `agency/src/test/java/io/casehub/agency/social/memory/evolution/` | Tests for memory hygiene evolution loop |
| `agency-core/src/main/java/io/casehub/agency/deliberation/conversation/` | Structured conversation protocol — projections, fold state, rendering, point classification, epistemic common ground, convergence detection |
| `agency/src/test/java/io/casehub/agency/deliberation/conversation/` | Tests for conversation protocol |
| `agency-core/src/main/java/io/casehub/agency/deliberation/conversation/orchestration/` | Conversation orchestrator — TurnPolicy SPI, termination conditions, PromptAssembler, ConversationOrchestrator composition root, ConversationListener per-dispatch callback |
| `agency/src/test/java/io/casehub/agency/deliberation/conversation/orchestration/` | Tests for conversation orchestrator |
| `agency-core/src/main/java/io/casehub/agency/deliberation/conversation/protocol/` | Conversation channel protocol — `ConversationProtocol` (`ChannelProtocol` impl with incremental `ChannelProjector` caching), `ConversationRules` (turn-policy, max-consecutive, convergence-threshold via `policyOverrides`), `DefaultConversationProjection` (concrete `ConversationProjection` with general-purpose defaults) |
| `agency/src/test/java/io/casehub/agency/deliberation/conversation/protocol/` | Tests for conversation channel protocol |
| `agency-core/src/main/java/io/casehub/agency/deliberation/normative/` | Normative conflict resolution — `ConflictResolutionStrategy<T>` SPI, `NormDecision<T>`, `NormResolution<T>`, five resolution strategies (priority, specificity, recency, most-restrictive, escalation) |
| `agency/src/test/java/io/casehub/agency/deliberation/normative/` | Tests for normative conflict resolution |
| `agency-core/src/main/java/io/casehub/agency/deliberation/negotiation/` | Negotiation channel protocol — `NegotiationProjection`, `NegotiationFold`, `AcceptancePolicy` SPI (unanimous, majority, threshold), `NegotiationRenderer`, termination conditions (`MaxRoundsTermination`, `DeadlineTermination`, `AcceptedTermination`, `TerminalOutcomeTermination`, `NegotiationCompositeTermination`) |
| `agency/src/test/java/io/casehub/agency/deliberation/negotiation/` | Tests for negotiation protocol |
| `agency-core/src/main/java/io/casehub/agency/governance/routing/` | Trust routing utilities — shared preference keys, policy resolver, compliance records |
| `agency/src/test/java/io/casehub/agency/governance/routing/` | Tests for routing utilities |
| `agency-core/src/main/java/io/casehub/agency/governance/routing/agent/` | AI-powered AgentRoutingStrategy implementations — LLM-reasoned and CBR-evidence agent selection, composable prompt enrichment pipeline, feature extraction SPI, outcome recording |
| `agency/src/test/java/io/casehub/agency/governance/routing/agent/` | Tests for AI routing strategies |
| `agency-core/src/main/java/io/casehub/agency/perception/rag/` | RAG retrieval worker factory — `RagRetrievalWorkerFactory` (static: `create()` with `QueryExtractionStrategy` + `CaseContextRetriever` + `List<CorpusRef>` → `Worker`). Provided dependency on `casehub-neocortex-rag-api`. |
| `agency/src/test/java/io/casehub/agency/perception/rag/` | Tests for RAG retrieval worker factory |
| `agency-core/src/main/java/io/casehub/agency/optimisation/prompt/` | DSPy-inspired prompt optimisation — core model, SPIs (`PromptOptimiser`, `PromptQualityMetric`, `PromptVariantStore`, `SystemPromptCustomiser`, `DiversityStrategy`), batch orchestration, A/B variant selection |
| `agency/src/test/java/io/casehub/agency/optimisation/prompt/` | Tests for prompt optimisation framework |
| `agency-core/src/main/java/io/casehub/agency/optimisation/prompt/optimiser/` | `PromptOptimiser` implementations — `FewShotOptimiser` (data-driven, diversity-aware via `DiversityStrategy`), `InstructionOptimiser` (LLM meta-prompting), `TopNDiversityStrategy` (identity), `OutcomeAwareDiversityStrategy` (MMR + Jaccard) |
| `agency/src/test/java/io/casehub/agency/optimisation/prompt/optimiser/` | Tests for prompt optimisers |
| `agency-core/src/main/java/io/casehub/agency/optimisation/prompt/runtime/` | CDI runtime beans — `OptimisedFewShotSection`, `VariantAwareSystemPromptCustomiser`, `WeightedOutcomeMetric`, `InMemoryPromptVariantStore` |
| `agency/src/test/java/io/casehub/agency/optimisation/prompt/runtime/` | Tests for prompt runtime beans |
| `agency-core/src/main/java/io/casehub/agency/optimisation/prompt/evolution/` | Evolution loop integration — `PromptOptimisationProposalSource` (`ImprovementProposalSource`), `PromptRegressionEvaluator` (`RegressionEvaluator`), `PromptOptimisationCategoryProvider` (`ImprovementCategoryProvider`), `PromptConflictStrategy` (`ConflictStrategy`), `PromptOutcomeProvider` (`@FunctionalInterface`) |
| `agency/src/test/java/io/casehub/agency/optimisation/prompt/evolution/` | Tests for evolution loop integration |
| `agency-core/src/main/java/io/casehub/agency/summarisation/` | Summarisation extensions (depends on summarisation-api) — `TieredContentSummariser<T>`, `SummaryMode`, observation package. Core types live in `summarisation-api/`. |
| `agency/src/test/java/io/casehub/agency/summarisation/` | Tests for summarisation extensions |
| `agency-core/src/main/java/io/casehub/agency/perception/llm/` | LLM-backed `ContentSummariser<T, SummaryResult>` — generic synthesis via `AgentProvider` |
| `agency/src/test/java/io/casehub/agency/perception/llm/` | Tests for LLM content summariser |
| `agency-core/src/main/java/io/casehub/agency/perception/observation/` | Observation accumulator — tiered, demand-driven rendering for LLM agent prompts with RAG-able chunks |
| `agency/src/test/java/io/casehub/agency/perception/observation/` | Tests for observation accumulator |
| `agency-core/src/main/java/io/casehub/agency/perception/observation/affordance/` | Affordance grounding + observation filtering — per-entity rendering, `AnnotatedSection` (capability-tagged sections with resolution alternatives), `ObservationFilter` SPI, `ObservationPipeline` (ordered filter composition), `PerceptionFilter` (visibility gating + resolution fallback), `ResolutionTier` |
| `agency/src/test/java/io/casehub/agency/perception/observation/affordance/` | Tests for affordance rendering and observation filtering |
| `agency/src/test/java/io/casehub/agency/summarisation/examples/clinical/` | Clinical temporal abstraction example (L1-L4 pipeline) |
| `agency/src/test/java/io/casehub/agency/summarisation/examples/logistics/` | Logistics hub monitoring example (L1-L4 pipeline) |
| `agency-core/src/main/java/io/casehub/agency/perception/narrative/` | Decision narrative pipeline — `DecisionSignal` sealed hierarchy (6 signal types including `ModelSelection`), `StepDecisionSummary`/`SignalDigest` (L1→L2 intermediate), `DecisionNarrative` (output), `DecisionSignalSummariser` (L1 heuristic), `DecisionNarrativeSummariser` (L2 LLM-backed `ContentSummariser` with template fallback), `NarrativeSignalStrategy` SPI, `AbstractNarrativeSignalStrategy` (base class), `DecisionNarrativePipeline` (factory wiring L1→L2) |
| `agency/src/test/java/io/casehub/agency/perception/narrative/` | Tests for decision narrative pipeline |
| `agency/src/test/java/io/casehub/agency/summarisation/examples/decision/` | Fsitrading-inspired decision narrative pipeline example (two-level: `TradingSignalSummariser` L1 + `TradeNarrativeSummariser` L2) |
| `speech-api/src/main/java/io/casehub/agency/speech/` | Speech pipeline SPIs — `SpeechPromptAssembler` (`@FunctionalInterface` SPI: prompt assembly for speech sessions), `AssembledPrompt` (system + user prompt record with optional model override), `ConversationTurn` (role + content record), `PromptContext` (agentId, tenantId, subjectId per-turn context), `PromptSection` (`@FunctionalInterface` SPI: contributes nullable text sections to prompts), `AvatarCognition` (composition root SPI: wraps assembler, initialize, tick, evaluateProactive, recordInteraction), `PhonemeAligner` (`@FunctionalInterface` SPI), `LipSyncEnricher` (decorator for phoneme timing enrichment) |
| `speech-api/src/test/java/io/casehub/agency/speech/` | Tests for LipSyncEnricher |
| `speech-sherpa/src/main/java/io/casehub/agency/speech/sherpa/` | sherpa-onnx FFM/Panama implementation — `SherpaOnnxSpeechToText` (+ `withDefaults()` zero-install factory), `SherpaOnnxTextToSpeech` (+ `withDefaults()` zero-install factory), `VitsTextToSpeech` (direct onnxruntime + espeak-ng for TTS with exact phoneme timing), `Audio8TextToSpeech` (DualAR autoregressive TTS via ORT with streaming + voice cloning, `withDefaults()` zero-install factories, `synthesiseStreaming()` with windowed overlap-subtract codec decoding, `registerVoice()`/`releaseVoice()` for zero-shot cloning), `Audio8Tokenizer` (GPT-2 byte-level BPE tokenizer from HuggingFace `tokenizer.json`), `Audio8Config` (model variant configuration — threads, temperature, top-k), `DualARLoop` (autoregressive slow AR + fast AR inference with KV-cache and Mamba SSM state, `iterateFrames()` for streaming), `CodecDecoder` (codec token matrix → PCM waveform via ORT), `VoiceRegistry` (voice registration/release lifecycle with transient codec encoder), `RuntimeManifest` (model metadata + streaming params from JSON — `codecHopLength`, `streamContextFrames`, `streamGuardFrames`), `EspeakPhonemeAligner` (`PhonemeAligner` impl — espeak-ng phonemization with proportional duration mapping, `withDefaults()` zero-config factory), `SherpaLibrary` (native binding via FFM SymbolLookup, 3-tier loading: system path → local cache → auto-download), `OnnxRuntimeLibrary` (onnxruntime C API FFM vtable bindings, singleton env shared across sessions), `EspeakLibrary` (espeak-ng C API FFM bindings), `SherpaLayouts` (C struct layouts for sherpa-onnx 1.10.x), `Provisioner` (auto-download native lib + model with SHA-256 verification, `FileLock` concurrency, `tar xf` extraction; `ensureGectorModel()` for GECToR ONNX bundles; `ensureAudio8Model()` for Audio8 DualAR models from HuggingFace), `ModelPatcher` (ONNX model duration output patching via protobuf), `VitsConfig` (Piper model config parsing + phoneme tokenization), `SentencePieceTokenizer` (pure Java Viterbi unigram segmentation — reads .model protobuf via CodedInputStream, trie-based vocab lookup), `GectorFilter` (`TextFilter` impl, destructiveness 3 — composes WordTokenizer + SentencePieceTokenizer + ORT + GectorTagApplier for iterative grammar correction), `GectorConfig` (tag vocabulary + verb dictionary + model path resolution from model directory), `GectorTagApplier` (stateless tag application — $KEEP/$DELETE/$APPEND/$REPLACE/morphological transforms), `WordTokenizer` (spaCy-compatible word pre-tokenization with contraction splitting), `WavReader`/`WavWriter` (PCM audio I/O), `SherpaConfig`, `SherpaException` |
| `speech-sherpa/src/test/java/io/casehub/agency/speech/sherpa/` | Tests for sherpa-onnx implementation |
| `speech-ws/src/main/java/io/casehub/agency/speech/ws/` | Avatar WebSocket endpoint — `SpeechWebSocket` (Quarkus WebSocket at `/ws/avatar`), `SpeechSession` (per-connection business logic), `VisemeMapping` (IPA→Oculus viseme with per-viseme weights and minimum duration enforcement), `PromptAssembler` SPI with `DefaultPromptAssembler`, `AvatarConfig` |
| `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/` | WebSocket protocol DTOs — `AvatarMessage` sealed hierarchy (Start, Stop, Partial, Transcript, Response, Phonemes, Error), `VisemeFrame` (viseme + timing + weight), `ConversationTurn`, `MessageCodec` (gson JSON encode/decode) |
| `speech-ws/src/test/java/io/casehub/agency/speech/ws/` | Tests for avatar WebSocket (88 tests) |

## Package: `io.casehub.agency.core.invoke`

Shared LLM invocation utility — eliminates the 17-site `AgentProvider.invoke()` → collect TextDelta → parse JSON boilerplate. Not CDI-managed — stateless static methods, caller passes `AgentProvider`.

| Class | What it does |
|-------|-------------|
| `StructuredAgentInvoker` | Static utility: `invokeText(AgentProvider, AgentSessionConfig) → InvocationResult<String>`, `invoke(AgentProvider, AgentSessionConfig, Class<T>) → InvocationResult<T>` (typed JSON), `invokeRaw(AgentProvider, AgentSessionConfig) → InvocationResult<JsonNode>`. Captures `InvocationComplete` for metadata. `stripFences()` removes markdown code fences. `extractJson()` locates embedded JSON in surrounding prose. |
| `InvocationResult<T>` | Sealed: `Success<T>(value, metadata)`, `ParseError<T>(rawResponse, parseError, metadata)`, `AgentError<T>(reason)`. Replaces ad-hoc null returns, exception swallowing, and log+fallback patterns. |
| `InvocationMetadata` | Record: `inputTokens`, `outputTokens`, `thinkingTokens`, `cacheReadTokens`, `cacheWriteTokens`, `totalCostUsd`, `durationMs`, `apiDurationMs`. Captured from `AgentEvent.InvocationComplete`. `EMPTY` sentinel for missing completion events. |
| `KeyedLock` | Per-key tick locking utility. `withLock(String key, Supplier<T>)` and `withLock(String key, Runnable)` — acquires a per-key `ReentrantLock`, executes, releases. `stateKey(agentId, tenantId)` composite key helper. Replaces 16 identical lock/try/finally sites across 13 orchestrator classes. |

## Package: `io.casehub.agency.governance.attestation`

Attestation write-path types and lifecycle observer SPI. `AttestationIntent` captures the full attestation payload; `AttestationIntentWriter` is the persistence SPI; `LifecycleAttestationObserver<E>` maps domain lifecycle events to attestation intents.

| Class | What it does |
|-------|-------------|
| `AttestationIntent` | Record: entryId, subjectId, verdict, confidence, capabilityTag, attestorId, actorType, attestorRole, dimensions (Map), evidence, namespace, causedByEntryId (nullable, for idempotent writes) |
| `AttestationIntentWriter` | SPI: `void write(AttestationIntent, String tenancyId)`. Implementations MUST honour the provided `entryId`. |
| `NoOpAttestationIntentWriter` | `@DefaultBean` `@ApplicationScoped` no-op implementation of `AttestationIntentWriter`. Consumers override with real persistence. |
| `LifecycleAttestationObserver<E>` | `@FunctionalInterface` SPI: `List<AttestationIntent> observe(E event, AttestationContext)`. Domain repos implement per event type. Returns non-null (empty list for irrelevant events). |
| `AttestationContext` | Record: tenancyId, caseId, capabilityTag — ambient context for observers |

## Package: `io.casehub.agency.governance.trust`

Trust-lifecycle SPIs with no compile-time attestation dependency. Intake classification and vouch orchestration.

| Class | What it does |
|-------|-------------|
| `IntakeClassifier<S>` | `@FunctionalInterface` SPI: `IntakeResult classify(S subject, IntakeContext)`. Generic subject — not coupled to trust infrastructure. |
| `IntakeContext` | Record: tenancyId, capabilityTag (nullable), attributes (Map escape hatch). Compact constructor defaults attributes to empty. |
| `IntakeResult` | Record: lane (String, domain-defined), confidence [0,1] (validated), reason, metadata. Compact constructor defaults metadata to empty. |
| `VouchConstraint` | SPI: `VouchEligibility check(VouchRequest)`. Pluggable eligibility check. |
| `VouchEligibility` | Sealed: `Eligible()`, `Ineligible(String reason)` |
| `VouchRequest` | Record: voucherId, voucheeId (UUID), capabilityTag, tenancyId, voucherActorType, voucherRole, namespace (nullable), attributes |
| `VouchResult` | Sealed: `Accepted(UUID attestationEntryId)`, `Rejected(List<String> reasons)` |
| `VouchService` | Orchestrator: runs all constraints (all-must-pass), writes ENDORSED attestation via `AttestationIntentWriter`. Not CDI-managed — consumer constructs with domain-specific constraints. |

## Package: `io.casehub.agency.communication.dispatch`

| Class | What it does |
|-------|-------------|
| `ChannelMessageMeta` | Sentinel-prefixed key=value metadata headers in message bodies. Apps choose their own sentinel. Methods: `parseMeta()`, `bodyContent()`, `encode()`, `parseInt()` |
| `ContextTracker` | Incremental LLM context window usage tracking via atomic counters. Thread-safe. |
| `ContextSnapshot` | Immutable record of context state: contribution chars, window size, effective %, threshold exceeded |
| `BoundedProjectionDecorator<S>` | Generic decorator wrapping any qhorus `ChannelProjection<S>` — skips messages past a configurable bound. Consumer supplies the value extraction function. |
| `ChannelAgentHandler` | SPI interface for sub-task handlers: `handles()`, `prepareTask()`, `buildResponse()`. First-match routing. |
| `ChannelAgentDispatcher` | First-match handler routing + agent invocation. Takes `Function<AgentTask, String>` (agent provider) and `Consumer<MessageDispatch>` (message sink). Subclass to override `onError()`. |
| `ChannelAgentRequest` | Record: channelId, correlationId, message (the sub-task trigger) |
| `AgentTask` | Record: systemPrompt, assembledInput (what to send to the LLM) |
| `AgentResultParseException` | Unchecked exception for handler parse failures |
| `ChannelEventAdapter<E>` | Direction 1 bridge: implements `MessageObserver`, extracts domain events via a `Function<MessageReceivedEvent, E>`, publishes `LevelEvent<E>` to an `EventStreamBus`. Null return from extractor filters. Extractor exceptions caught and logged. |
| `ChannelEventPublisher<E>` | Direction 2 bridge: subscribes to `EventStreamBus<E>`, converts events to `MessageDispatch` via a builder function, dispatches via `MessageDispatcher`. Best-effort — catches + logs, never propagates. |

## Package: `io.casehub.agency.communication.channel.summary`

Channel and thread summary integration — Message-specific `ContentSummariser<Message, SummaryResult>` implementations, the `SummaryUpdateHook` adapter for channel summaries, and the push-based `ThreadSummaryObserver` for per-thread summaries. `ContentSummariser` is now in `casehub-agency-summarisation-api` with generified `<T, R>` — consumers bind `SummaryResult` as `R`.

| Class | What it does |
|-------|-------------|
| `HeuristicMessageSummariser` | `@DefaultBean` `ContentSummariser<Message, SummaryResult>` — append-only structural summary from message metadata (participants, topics, time span). Merges annotations across invocations. Zero LLM cost. |
| `ChannelSummariser` | `@ApplicationScoped` `SummaryUpdateHook` adapter — delegates to injected `ContentSummariser<Message, SummaryResult>`. Mutiny-aware blocking, channel-context error logging. |
| `NoOpThreadSummaryStore` | `@DefaultBean` `@ApplicationScoped` no-op `ThreadSummaryStore` — `save()` returns input, queries return empty. Consumers override with qhorus persistence. |
| `ThreadSummaryObserver` | `@ApplicationScoped` push-based observer — detects DONE/FAILURE messages with correlationId, fetches thread messages via `CrossTenantMessageStore`, delegates to `ContentSummariser<Message, SummaryResult>`, writes to `ThreadSummaryStore`. Per-correlationId concurrency guard. Async via `ManagedExecutor`. |

## Package: `io.casehub.agency.deliberation.conversation`

Structured conversation protocol — reusable infrastructure for multi-agent deliberation channels. Extracted from drafthouse via casehubio/drafthouse#79, #80, #81, #83.

| Class | What it does |
|-------|-------------|
| `ConversationConstants` | Sentinel-based metadata encoding/decoding for structured conversation messages. Defines entry types, round markers, status transitions. |
| `ConversationProjection` | Incremental projection over conversation messages — maintains fold state, tracks rounds, classifies points. |
| `ConversationFold` | Fold operations for typed-message projections — accumulates conversation state from a message stream. |
| `ConversationState` | Immutable snapshot of conversation state: points by thread, round boundaries, flags, sub-task status. |
| `ConversationPoint` | Individual point in a conversation thread — classification, priority, content, agent attribution. |
| `ConversationRenderer` | Pluggable markdown rendering of conversation state — round-by-round or thread-by-thread views. |
| `ConversationRendererConfig` | Configuration for renderer: section ordering, inclusion filters, format options. |
| `ThreadEntry` | Entry within a conversation thread — point + responses + sub-task findings. |
| `PointClassification` | Open type system for classifying conversation points (replaces drafthouse's closed `EntryType` enum). |
| `Priority` | Priority level for conversation points — used in rendering and attention ordering. |
| `RoundMemo` | Summary memo for a completed conversation round — key outcomes, unresolved points. |
| `FlagEntry` | Flag raised during conversation — attention markers for moderators or supervisors. |
| `SubTaskFinding` | Result from a sub-agent task (verify, analyse, etc.) attached to a conversation point. |
| `EpistemicStatus` | Enum: ESTABLISHED, PENDING, DISPUTED — classification outcome for common ground derivation. |
| `ParticipantContext` | Pre-computed participant tracking per point: allParticipants, respondedBy, acknowledgedBy, completedBy, disputedBy, failedBy, roundsSinceLastActivity. |
| `EpistemicRule` | `@FunctionalInterface` strategy for classifying conversation points by epistemic status. Composable via `and()` (conservative) / `or()` (permissive). |
| `EpistemicRules` | Three provided rules: `explicitAcknowledgement(minParticipants)`, `tacitAcceptance(windowRounds)`, `commitmentResolution()`. |
| `GroundedFact` | Epistemic metadata per point: pointId, topic, status, content, acknowledgedBy, disputedBy, round. |
| `CommonGroundState` | Derived view partitioning conversation points into establishedFacts, pendingClaims, disputedPoints. |
| `CommonGroundAnalyser` | Stateless utility: `analyse(ConversationState, EpistemicRule) → CommonGroundState`. Builds `ParticipantContext` per point and delegates classification to the rule. |
| `ConvergenceState` | Enum: PROGRESSING, CONVERGING, CONSENSUS, DEADLOCK, DIMINISHING_RETURNS. |
| `ConvergenceSignal` | Record: state, confidence (0.0–1.0), reason (human-readable). |
| `ConvergenceContext` | Pre-computed convergence indicators: totalPoints, established/pending/disputed counts, recentSimilarity, messageLengthTrend, roundsSinceNewPoint, roundsSinceStatusChange, recentMessageTypeCounts. |
| `ConvergencePolicy` | `@FunctionalInterface` strategy for evaluating convergence from conversation and common ground state. |
| `ConvergencePolicies` | Three provided policies: `structural(similarityThreshold, staleRounds)`, `commonGroundRatio(consensusThreshold, deadlockDisputeRatio)`, `composite(policies...)`. |
| `ConvergenceAnalyser` | Stateless utility: `analyse(ConversationState, CommonGroundState, ConvergencePolicy, recentWindow) → ConvergenceSignal`. |
| `RenderContext` | Supplementary render-time inputs: reactions, commonGround, convergence, progress. Replaces renderer overloads. |
| `ProgressRenderer` | `@FunctionalInterface` SPI: `String render(ProgressInstance)`. Pluggable progress-to-text rendering for LLM agent prompts. |
| `DefaultProgressRenderer` | Built-in `ProgressRenderer`: percentage (`"Label: N%"`), count (`"Label: N of M"`), step (`"a ✓ → b ⏳ → c ○"`), fallback to status name. Null-safe. |

## Package: `io.casehub.agency.deliberation.conversation.orchestration`

Autonomous multi-agent conversation orchestrator — composes `ConversationProjection`, `PartitionedObservationService`, pluggable turn policies, and `TerminationCondition` into a self-driving conversation loop.

| Class | What it does |
|-------|-------------|
| `ConversationOrchestrator` | Composition root — iterative queue-based loop. `converse(MessageView) → Uni<ConversationOutcome>`. Participants passed at construction; observation service auto-registers observers. `terminate()` for external stop (volatile flag). |
| `ConversationOutcome` | Record: `finalState`, `terminationDecision`, `agentResults`, `dispatchCount`, `elapsed` |
| `TurnPolicy` | SPI: `nextResponders(ConversationState, TurnContext, List<AgentParticipant>) → List<AgentParticipant>`. Synchronous. Empty list = silence. |
| `TurnContext` | Record: `senderId`, `@Nullable targetId`, `entryType`, `metadata`. Extracted from `MessageView` by orchestrator. |
| `AgentParticipant` | Record: `agentRef` (AgentRef), `role`, `systemPrompt`. `agentId()` delegates to `agentRef.name()`. |
| `PromptAssembler` | `@FunctionalInterface`: `assemble(AgentParticipant, PartitionedDrain<String>, ConversationState) → String`. Per-agent prompt construction. |
| `ResponseMessageBuilder` | `@FunctionalInterface`: `build(AgentParticipant, AgentResult, ConversationState) → MessageView`. Converts agent output to foldable message. |
| `ConversationListener` | `@FunctionalInterface`: `onDispatch(ConversationState, TerminationDecision, int, Duration)`. Per-dispatch callback fired after each termination check in `converse()`. Optional — wire via 10-arg constructor. |
| `RoundRobinTurnPolicy` | Strict alternation. Stateless — derives next from sender ID in participant order. |
| `AddressedTurnPolicy` | Respond when `targetId` matches agent role. Null target = silence. |
| `PointAddressedTurnPolicy` | Respond to unresolved OPEN/ACTIVE points not yet responded to by agent's role. |
| `FreeTurnPolicy` | All participants except sender. Pair with `MaxIterationsTermination`. |
| `AllAgreedTermination` | `TerminationCondition<ConversationState>` — Complete when all points have a resolved status (configurable set). |
| `SupervisorTermination` | `TerminationCondition<ConversationState>` — Complete when supervisor role signals end via configurable entry type. |
| `ContestedEscalation` | `TerminationCondition<ConversationState>` — Escalate when DISPUTED points exceed threshold. |
| `CompositeTermination` | `TerminationCondition<ConversationState>` — evaluates conditions in order; first non-Continue wins. |

## Package: `io.casehub.agency.deliberation.normative`

Generic normative conflict resolution — resolves conflicts between competing norm decisions from multiple sources. Speculative build (no consumer yet). Primary expected consumer: oversight pipeline composing `NormDecision<RiskDecision>`.

| Class | What it does |
|-------|-------------|
| `NormSpecificity` | Enum: UNIVERSAL, DOMAIN, TENANT, CASE_TYPE, INSTANCE. `isMoreSpecificThan()` for lex specialis comparison. |
| `ResolutionMethod` | Enum: PRIORITY, SPECIFICITY, RECENCY, MOST_RESTRICTIVE, ESCALATION. Identifies which strategy produced a resolution. |
| `NormDecision<T>` | Record: source, decision (generic T), priority, specificity, establishedAt. Wraps any decision with norm metadata. |
| `NormResolution<T>` | Record: winner (NormDecision), overridden (List), reason, method. Full audit trail of resolution outcome. |
| `ConflictResolutionStrategy<T>` | `@FunctionalInterface`: `NormResolution<T> resolve(List<NormDecision<T>>)`. Core SPI. |
| `PriorityResolution<T>` | Lowest priority int value wins. |
| `SpecificityResolution<T>` | Most specific norm wins (lex specialis). |
| `RecencyResolution<T>` | Most recently established norm wins (lex posterior). |
| `MostRestrictiveResolution` | Typed to `RiskDecision` — GateRequired beats Autonomous. Backward-compatible with ChainedActionRiskClassifier behavior. |
| `EscalationResolution<T>` | Always escalates to a designated decision when any conflict exists. |

## Package: `io.casehub.agency.deliberation.negotiation`

Negotiation channel protocol — reusable `ChannelProjection<NegotiationState>` for proposal/counter-proposal exchange. Supports bilateral (two-party alternating) and mediator-coordinated multilateral (N-party with configurable quorum). Uses PROPOSE MessageType (qhorus#395) for commissive speech acts. Party set required upfront for AcceptancePolicy correctness.

| Class | What it does |
|-------|-------------|
| `NegotiationOutcome` | Enum: PENDING, AGREED, DEADLOCKED, WITHDRAWN. `isTerminal()` for non-PENDING. |
| `ProposalStatus` | Enum: ACTIVE, SUPERSEDED, ACCEPTED, REJECTED. `isTerminal()` for ACCEPTED/REJECTED. |
| `PartyDecision` | Enum: ACCEPTED, REJECTED |
| `Proposal` | Record: proposalId, proposer, content, round (1-based), createdAt, status |
| `Response` | Record: party, decision, reason (@Nullable), respondedAt |
| `NegotiationState` | Record: proposals (ordered chain), parties, responses (per-party to active proposal), outcome. `activeProposal()`, `round()`, `hasActiveProposal()`. |
| `NegotiationFold` | Pure static state transitions: `propose()`, `accept()`, `reject()`, `agree()`, `deadlock()`, `withdraw()`. Counter-proposals supersede active proposal and clear responses. |
| `NegotiationProjection` | Concrete `ChannelProjection<NegotiationState>`. Constructor: `(Set<String> parties, AcceptancePolicy)`. Dispatches on PROPOSE/DONE/DECLINE. apply() never throws. Unknown senders ignored. |
| `AcceptancePolicy` | `@FunctionalInterface`: `boolean isAccepted(NegotiationState)`. Pluggable quorum evaluation. |
| `UnanimousAcceptance` | All non-proposer parties must accept |
| `MajorityAcceptance` | >50% of non-proposer parties must accept |
| `ThresholdAcceptance` | At least N acceptances required |
| `NegotiationRenderer` | Markdown rendering: current proposal, responses, pending parties, proposal history |
| `MaxRoundsTermination` | `TerminationCondition<NegotiationState>` — Complete at max rounds |
| `AcceptedTermination` | `TerminationCondition<NegotiationState>` — Complete when AGREED |
| `TerminalOutcomeTermination` | `TerminationCondition<NegotiationState>` — Complete for AGREED, Failed for DEADLOCKED/WITHDRAWN |
| `DeadlineTermination` | `TerminationCondition<NegotiationState>` — Complete when latest proposal exceeds deadline |
| `NegotiationCompositeTermination` | First-non-Continue-wins composition of termination conditions |
| `NegotiationConstants` | Constants for negotiation outcome strings |
| `agency-core/src/main/java/io/casehub/agency/deliberation/protocol/` | Dual-mode protocol infrastructure — `ProtocolStateEvaluator` (`@FunctionalInterface` strategy for state-source-agnostic protocol evaluation), `PlaybookStateEvaluator` (delegates to platform `PlaybookProtocolEvaluator`, maps `ProtocolAdvisory` → `DispatchAdvisory`), `AdvisoryMapper` (exhaustive severity/action mapping), `PlaybookProtocolLifecycleHandler` (`ProtocolLifecycleHandler` impl — template loading, recovery replay, instance eviction) |
| `agency/src/test/java/io/casehub/agency/deliberation/protocol/` | Tests for dual-mode protocol infrastructure |
| `agency-core/src/main/java/io/casehub/agency/deliberation/negotiation/protocol/` | Negotiation channel protocol — `NegotiationProtocol` (dual-mode `ChannelProtocol` impl dispatching to projection or playbook evaluator via `negotiation.state-backing` policyOverride), `NegotiationProjectionEvaluator` (`ProtocolStateEvaluator` with incremental `ChannelProjector` caching), `NegotiationRules` (builder-configurable pure rules function with `policyOverrides` mapping) |
| `agency/src/test/java/io/casehub/agency/deliberation/negotiation/protocol/` | Tests for negotiation channel protocol |
| `agency-core/src/main/java/io/casehub/agency/deliberation/judgment/protocol/` | Judgment channel protocol — `JudgmentProtocol` (`ChannelProtocol` impl with incremental `ChannelProjector` caching), `JudgmentRules` (agreement, min-reviewers, escalation via `policyOverrides`), `JudgmentProjection` (`ChannelProjection<JudgmentState>` — DONE → approve, DECLINE → reject), `JudgmentState`, `JudgmentFold`, `JudgmentVerdict`, `JudgmentOutcome` |
| `agency/src/test/java/io/casehub/agency/deliberation/judgment/protocol/` | Tests for judgment channel protocol |

## Package: `io.casehub.agency.core`

Compositional agentic orchestration framework — eight sub-packages implementing five SPIs for routing, decomposition, activation, aggregation, and termination, plus execution drivers and pre-composed pattern builders.

| Sub-package | What it contains |
|-------------|-----------------|
| `core` | Foundation types: `AgentRef` (sealed: WorkerAgent, ChannelAgent, HumanAgent, ExternalAgent, ComposedAgent; extends `ExecutorRef` — each variant implements `name()`/`description()`), `AgentResult`, `RoutingCandidate`, `FailurePolicy`, `AgentCardSupport` (shared card-building via `ExecutorRef`; no more pattern matching). **Uni policy:** SPIs return direct types (not Uni) — virtual threads make blocking free. Only `AgentInvoker.invoke()` (concurrent dispatch) and API boundaries (`execute()`, `converse()`) keep Uni. |
| `orchestration.routing` | Routing SPI: `RoutingStrategy<T>` (`RoutingDecision route(RoutingContext<T>)` — direct return, no Uni), `RoutingDecision` (sealed: Selected, Unresolvable, Escalate), `FirstMatchRouting`, `RoundRobinRouting`, `SequentialRouting`, `LlmSelectedRouting` |
| `orchestration.decomposition` | Decomposition SPI: `DecompositionStrategy<T>`, `TaskNode` (sealed: LeafTask \| CompoundTask; LeafTask non-sealed: PrimitiveTask, PlannedTask; both carry `id`, `createdAt`, `status()` → PENDING, `executor()`, optional `OutputContract`), `DecompositionMethod`, `DecompositionContext`, `AgenticDecompositionContext` (agency's `DecompositionContext<T>` impl — carries `agents`, `planningConstraints`; overrides `constraints()`; nullable fields default safely in compact constructor), `IdentityDecomposition`, `StaticDecomposition` (first-match guard evaluation; pre-filters methods whose `estimatedCost`/`estimatedDuration` exceed `context.constraints()` before guard evaluation), `ForwardReasoningDecomposition` (SHOP-style forward reasoning — applies `PrimitiveTask.effect()` to projected state during planning), `LlmDecomposition` (recursive multi-level planning via `maxDepth` — default 1 = flat; subtask entries become `CompoundTask` nodes recursively decomposed; depth-aware prompts; hierarchical context enrichment), `HybridDecomposition` (static→LLM fallback with `staticFailureHint`; `maxDepth` pass-through constructors), `CapabilityDependencyDecomposition` (GOAP backward-chaining), `HeuristicDecomposition` (ranked method selection via pluggable `DecompositionHeuristic<T>` with backtracking on `NoMethodMatchedException`; context-carried decomposer propagates heuristic through `SequenceStrategy` children), `DecompositionHeuristic<T>` (`@FunctionalInterface` SPI — batch-native async method scoring, higher-score-is-better), `ScoredMethod<T>` (record: method + score), `StructuralCostHeuristic` (zero-LLM tree-walking cost estimation), `LlmDecompositionHeuristic` (online LLM method evaluation via `AgentProvider`), `CompositeHeuristic` (weighted normalized combination of multiple heuristics with completeness contract validation), `Tasks` (DSL: `primitive()`, `planned()`, `compound()`, `decompose()` factories), `OutputContract` (`@FunctionalInterface` — `nonNull()`, `type()`, `of()`, composable via `and()`), `NoMethodMatchedException` (carries `methodCount`) |
| `orchestration.activation` | Activation SPI: `ActivationRule<T>`, `ActivationContext`, `OnExplicitDispatch`, `MaxIterationsGuard` |
| `orchestration.aggregation` | Aggregation SPI: `AggregationStrategy<T>`, `AggregationResult` (sealed: Resolved, Partial, Deadlocked), `PassThrough`, `CollectAll`, `MajorityVote`, `AuctionAggregation` (English/Dutch iterative auction — `Bid`, `AuctionState`, `AuctionOutcome`, `BidExtractor` SPI, `AuctionType`) |
| `communication.channel` | Inter-agent channel infrastructure + supervisor observation: `ChannelBinding` (channelId + semantic), `ChannelConfig` (channelManager, dispatcher, semantic, protocols), `ChannelExecutionStrategy<T>` (sealed: Conversation, FanIn, Barrier), `ConversationChannelAdapter`, `DefaultConversationProjection`, `ChannelObserver<S>` (implements `MessageObserver` + `EventSource`; folds channel messages through `ChannelProjection<S>` via AtomicReference; `terminateWhen()`, `asTermination()` factory methods; builder for multi-channel observation), `ChannelTeardownListener` |
| `deliberation.coalition` | Coalition formation: `CoalitionProposal`, `CoalitionEvaluator` SPI, `CoalitionScore`, `CoalitionContext`, `CapabilityCoverageEvaluator` |
| `deliberation.intention` | Joint intentions (Bratman): `JointIntention` (form/activate/reconsider/drop/fulfill lifecycle), `IntentionMonitor` SPI, `ReconsiderationSignal`, `ReconsiderationReason`, `IntentionStatus` |
| `deliberation.belief` | Formal belief revision (AGM): `BeliefSet<T>` (expand/contract/revise), `Belief<T>` (with entrenchment ordering), `ConsistencyChecker<T>` SPI |
| `orchestration.termination` | Termination SPI: `TerminationCondition<T>` (composable via `or()`/`and()` with priority: Escalate > Failed > Complete > Continue), `TerminationDecision` (sealed: Continue, Complete, Failed, Escalate), `GoalReached`, `MaxIterationsTermination`, `JudgeConvergence`, `ConvergenceTermination` (bridges `ConvergencePolicy` → `TerminationCondition`) |
| `orchestration.execution` | Execution model: `ExecutionModel<T>` (carries `PatternType`), `ExecutionDriver<T>`, `AbstractExecutionDriver`, `OrchestratedDriver`, `ChoreographedDriver` (two modes: legacy continuous-loop, event-driven via `EventSource` + `EventConcurrencyPolicy` + `BlockingQueue<DriverEvent>`; `signal()` for direct push; `cancel()` posts cancellation event to break `queue.take()`), `DriverEvent` (typed event record: source, timestamp, payload), `EventSource` (`@FunctionalInterface` SPI for event delivery with `Cancellation`, `merge()`, `ticker()` factories), `EventConcurrencyPolicy` (pluggable policy: `serialize()`, `coalesce()`, `coalesceBySource()`, composable via `.then()`), `AgentInvoker<T>` (handles ExternalAgent + ComposedAgent; `withFallback()` for composition), `ExecutionBackend<T>` (pluggable execution — `reactive()` default via `CancellableBackend`; `choreographed(policy, sources...)` for event-driven; `cancel()` default method for external cancellation), `PatternType` (SEQUENCE/PARALLEL/LOOP/CONDITIONAL workflow-shaped; SUPERVISOR/DEBATE/VOTING/HTN custom-driver; `toCapabilityType()` bridge), `CapabilityCategory` (8 categories: ORCHESTRATION/DELIBERATION/PERCEPTION/COMMUNICATION/GOVERNANCE/OPTIMISATION/SOCIAL/PLAYBOOK), `CapabilityType` (28 values — exhaustive catalog of all CaseHub capabilities; each carries `category()` and `toPatternType()` bridge), `ExecutionResult` (sealed: Completed, Failed, Escalated, Cancelled), `ExecutionState` (sealed: Idle, Running, WaitingForAgent, WaitingForEvent, Complete, Faulted, Cancelled), `ExecutionEventListener` |
| `orchestration.listener` | Accountability listeners: `OrchestrationEventType`, `EventLogListener` (operational audit via EventSink), `LedgerExecutionListener` (compliance audit via LedgerSink), `MetricsListener` (OTel metrics via Meter) |
| `orchestration.pattern` | Pattern DSL: `Patterns` entry point, `AbstractPatternBuilder`, 8 builders (Supervisor, Sequence, Loop, Parallel, Voting, Debate, Conditional, HTN) |

## Package: `io.casehub.agency.governance.routing`

Shared trust routing utilities — eliminates duplicated preference-to-policy boilerplate across domain repos.

| Class | What it does |
|-------|-------------|
| `DoublePreference` | `SingleValuePreference` record for double-typed preference values. Replaces copies in aml, devtown, life. |
| `IntPreference` | `SingleValuePreference` record for int-typed preference values. Replaces copies in aml, devtown. |
| `TrustRoutingPolicyKeys` | Parameterised `PreferenceKey` definitions — scope prefix + 4 universal keys (threshold, minimum-observations, borderline-margin, blend-factor) + builder for domain-specific quality floor keys. |
| `TrustRoutingPolicyResolver` | Stateless utility: `resolve(Preferences, TrustRoutingPolicyKeys)` → `TrustRoutingPolicy`. Also exposes `collectFloors()` for hybrid providers that read some fields from a domain registry. |
| `RoutingDecisionRecord` | Compliance audit record for trust-weighted routing decisions: capabilityTag, workerId, trustScoreAtRouting, thresholdApplied, evidenceEntryId. |
| `TrustRoutingRequirement` | Compliance evidence wrapper: requirementId, citation, mechanism, status, decisions. |
| `RequirementStatus` | Enum: CLOSED, PARTIAL, BREACHED, GAP. |

## Package: `io.casehub.agency.perception.rag`

Reusable RAG retrieval worker factory — eliminates per-domain boilerplate for producing engine `Worker` instances that extract queries, retrieve chunks from neocortex corpora, and return mapped results. Provided dependency on `casehub-neocortex-rag-api`.

| Class | What it does |
|-------|-------------|
| `RagRetrievalWorkerFactory` | Static factory: `create(name, capabilityName, CaseContextRetriever, QueryExtractionStrategy, List<CorpusRef>, maxResults) → Worker`. Default overload with maxResults=10. Worker function calls `CaseContextRetriever.retrieve()`, maps chunks via `toMap()`, returns `WorkerResult` with `retrievedChunks` + `summary`. |

## Package: `io.casehub.agency.governance.routing.agent`

AI-powered `AgentRoutingStrategy` implementations for the engine's routing pipeline, plus composable prompt enrichment and outcome recording infrastructure. Strategies are selected by name via `StrategyResolver` (engine#634). Optional trust classification via `Instance<T>` — activates when engine-ledger is on the consumer's classpath.

| Class | What it does |
|-------|-------------|
| `LlmAgentRoutingStrategy` | `AgentRoutingStrategy` (id: `"llm"`). Asks an LLM via `AgentProvider` to reason about which candidate best fits the task. Delegates to `RoutingPromptAssembler` for composable prompt enrichment (CBR history, future signal sources). Optional trust classification. Worker pool offloading. |
| `CbrAgentRoutingStrategy` | `AgentRoutingStrategy` (id: `"cbr"`). Reads pre-retrieved experiences from `AgentRoutingContext.experiences()` and analyses worker success rates with configurable `CbrOutcomeWeights`, similarity-weighted scoring, and `RoutingSignalAssembler` integration. Falls back to `AgentGraphQuery.topAgentsByOutcome()` when CBR produces no match. Optional trust classification. |
| `CbrOutcomeWeights` | SPI for step-level routing outcome weights used by `CbrAgentRoutingStrategy`. Returns `Map<RoutingOutcome, Double>`. Domain repos override `DefaultCbrOutcomeWeights` with `@ApplicationScoped`. |
| `DefaultCbrOutcomeWeights` | `@DefaultBean` — SUCCESS=1.0, GATE_EXPIRED=0.5, GATE_REJECTED=0.25, FAILURE=0.0. |
| `CbrCaseOutcomeWeights` | SPI for case-level outcome weights used by `PlanCompositionAnalyser`. Returns `Map<String, Double>` (string keys — case outcomes are domain-dependent). |
| `DefaultCbrCaseOutcomeWeights` | `@DefaultBean` — COMPLETED=1.0, FAULTED=0.2, CANCELLED=0.0. |
| `PlanCompositionAnalyser` | `RoutingSignalProvider` (id: `"plan-composition"`). Scores candidates based on case-level outcomes in multi-step plans (planTrace.size >= 2). Uses `CbrCaseOutcomeWeights` for case outcome weighting and similarity-weighted scoring. Returns null when no multi-step plan data exists. |
| `CbrRoutingPromptSection` | `RoutingPromptSection` implementation — formats historical CBR outcomes per eligible agent for LLM routing prompts. |
| `CoordinationSignalProvider` | `RoutingSignalProvider` (id: `"coordination"`). Scores candidates by historical team composition outcomes with adaptation-guided retrieval (AGR). Extracts team membership from experience plan traces, computes weighted mean of case-level outcomes where each candidate appeared in a multi-agent team, and weights by team re-assembly feasibility (`\|team ∩ candidatePool\| / \|team\|`). Uses `CoordinationOutcomeWeights` for case outcome weighting. |
| `CoordinationOutcomeWeights` | SPI for case-level outcome weights used by `CoordinationSignalProvider`. Returns `Map<String, Double>` (string keys — case outcomes are domain-dependent). Domain repos override `DefaultCoordinationOutcomeWeights` with `@ApplicationScoped`. |
| `DefaultCoordinationOutcomeWeights` | `@DefaultBean` — COMPLETED=1.0, FAULTED=0.2, CANCELLED=0.0. |
| `RoutingSupport` | Package-private utility — shared prompt building, response parsing, `AgentProvider` invocation, and trust classification extraction (`TrustFilterOutcome` sealed interface). Used by both `LlmAgentRoutingStrategy` and `CbrAgentRoutingStrategy`. |
| `PredecessorAnalyser` | `RoutingSignalProvider` (id: `"predecessor"`). Scores candidates based on immediate predecessor context in historical plan traces — sorts steps by priority, finds steps matching target capability, scores by case outcome weighted by similarity with predecessor (capability:worker) pair in reason. |
| `DispositionAwareRouting` | `RoutingSignalProvider` (id: `"disposition"`). Scores candidates by personality/disposition match against a desired `DispositionProfile` extracted from case context (`_routing.disposition.<capabilityName>` or `default`). Exact-match scoring per `DispositionAxis` with optional per-axis weights. No-op when profile absent or no candidates have dispositions. |
| `ModelPreferenceSignalProvider` | `RoutingSignalProvider` (id: `"model-preference"`). Scores candidates by `(workerId, modelId)` compound key from historical experiences. Reads `modelId` from `ExperiencePlanStep.parameters()`. Similarity-weighted success rates using `CbrOutcomeWeights`. Activates when engine records model data (engine#1096). |
| `DispositionProfile` | Record: `Map<DispositionAxis, String> desired` + `Map<DispositionAxis, Double> weights`. Compact value type for desired agent personality traits. |

## Package: `io.casehub.agency.optimisation.prompt`

DSPy-inspired prompt optimisation framework — offline batch cycle that auto-improves LLM routing and decomposition prompts based on CBR outcome feedback. Few-shot example curation, instruction refinement, deterministic A/B testing with circuit breaker safety rails. Pure Java core; CDI runtime beans in sub-packages.

| Class | What it does |
|-------|-------------|
| `PromptSignature` | Record: declares an optimisation target — id, description, base system prompt, I/O types. Passed to batch by consumers. |
| `PromptVariant` | Record: versioned optimisation bundle — examples, instruction delta, quality score, lineage pointer, consecutive wins. |
| `FewShotExample` | Record: curated example for prompt enrichment — input, output, outcome, quality score, annotation. |
| `VariantOutcome` | Record: outcome correlated with its variant — generic string outcome (not routing-specific). |
| `ExampleCandidate` | Record: pre-rendered case for example selection — batch constructs from `PlanCbrCase` data. |
| `OptimisationDataset` | Record: outcome summaries + full case data for example selection. |
| `OptimiserResult` | Record: output from a `PromptOptimiser` — examples + instruction delta. |
| `OptimiserConfig` | Record: batch configuration — maxExamples (5), minQualityThreshold (0.7), minOutcomeCount (50), minVariantOutcomes (20). |
| `SafetyConfig` | Record: safety rails — quality floor (0.3), max experiment cycles (5), max experiment age (30 days), circuit breaker threshold (5), master switch. |
| `BatchResult` | Sealed interface: AlreadyRunning, InsufficientData, NoImprovement, VariantCreated, VariantPromoted. |
| `PromptOptimiser` | SPI: teleprompter equivalent — `optimise(signature, currentVariant, dataset, config)`. Two implementations in `.optimiser` sub-package. |
| `PromptQualityMetric` | `@FunctionalInterface` SPI: scores variant performance from a list of `VariantOutcome`. |
| `PromptVariantStore` | SPI: holds active variants — store, getActive (by slot), getHistory, activate. |
| `SystemPromptCustomiser` | `@FunctionalInterface` SPI: customises base system prompts with instruction deltas from active variants. |
| `DiversityStrategy` | `@FunctionalInterface` SPI: `List<ExampleCandidate> select(shortlist, maxExamples)`. Pluggable re-ranking for `FewShotOptimiser`. Contract: subset of input, ≤ maxExamples, no mutation. |
| `VariantSelector` | Deterministic A/B split via `(hash & 0x7FFFFFFF) % 100`. Per-capability circuit breaker trips after consecutive experiment failures. |
| `PromptOptimisationBatch` | Batch orchestrator — gate check, score variants, run optimisers, build candidate, promotion decision (consecutive wins required), concurrency guard (per-signature lock). |

### Sub-package: `io.casehub.agency.optimisation.prompt.optimiser`

| Class | What it does |
|-------|-------------|
| `FewShotOptimiser` | `PromptOptimiser` (id: `"few-shot"`). Diversity-aware — filters by quality, scores by `qualityScore × similarityScore`, takes 2× shortlist, delegates to `DiversityStrategy` for final selection. No-arg constructor uses `TopNDiversityStrategy` (backward compatible). |
| `InstructionOptimiser` | `PromptOptimiser` (id: `"instruction"`). LLM meta-prompting — analyses outcome patterns, asks LLM to generate instruction refinements. Requires `AgentProvider`. Graceful degradation on LLM failure. |
| `TopNDiversityStrategy` | `DiversityStrategy` identity implementation — returns first N candidates from pre-sorted shortlist. Used as default. |
| `OutcomeAwareDiversityStrategy` | `DiversityStrategy` with outcome-category seeding + token-level Jaccard MMR. Constructor takes `diversityWeight` [0,1]. Skips seeding at 0.0 (pure relevance). Case-insensitive outcome grouping. |

### Sub-package: `io.casehub.agency.optimisation.prompt.runtime`

| Class | What it does |
|-------|-------------|
| `OptimisedFewShotSection` | `RoutingPromptSection` — injects curated few-shot examples from active variant into LLM routing prompts. Discovered via CDI alongside `CbrRoutingPromptSection`. |
| `VariantAwareSystemPromptCustomiser` | `SystemPromptCustomiser` `@DefaultBean` — reads instruction delta from active variant, appends to base system prompt. |
| `WeightedOutcomeMetric` | `PromptQualityMetric` `@DefaultBean` — maps outcome strings to weights (SUCCESS=1.0, GATE_EXPIRED=0.5, GATE_REJECTED=0.25, DECLINED=0.0, FAILURE=0.0). Unknown outcomes default to 0.0. |
| `InMemoryPromptVariantStore` | `PromptVariantStore` `@DefaultBean` — in-memory with atomic write semantics (write-fsync-rename). |

### Sub-package: `io.casehub.agency.optimisation.prompt.evolution`

Evolution loop integration — registers prompt optimisation as a domain participant in the engine's continuous evolution pipeline (engine#1115/1148). All implementations use domain ID `"prompt-optimisation"`.

| Class | What it does |
|-------|-------------|
| `PromptOutcomeProvider` | `@FunctionalInterface` SPI: `List<VariantOutcome> outcomes(String signatureId)`. Consumer-supplied outcome data retrieval. |
| `PromptOptimisationProposalSource` | `ImprovementProposalSource` impl (sourceId: `"prompt-optimisation"`). Scans registered `PromptSignature` instances, evaluates quality via `PromptQualityMetric`, proposes tuning when quality drops below threshold. |
| `PromptRegressionEvaluator` | `RegressionEvaluator` impl (evaluatorId: `"prompt-quality"`). Compares per-signature quality scores in `HealthScoreSnapshot.componentScores()` (keyed with `prompt:` prefix). Reports regression when any signature drops by more than threshold (default 0.1). |
| `PromptOptimisationCategoryProvider` | `ImprovementCategoryProvider` impl. Single category `"prompt-tuning"`, four stages: evaluate → optimise → promote → outcome-recording. No approval gates. |
| `PromptConflictStrategy` | `ConflictStrategy` impl. Signature-level mutual exclusion — conflicts when an active improvement targets the same `PromptSignature.id()` via metadata key `signatureId`. |

## Package: `io.casehub.agency.social.cognition.evolution`

Cognitive evolution ticker — wires engine's `EvolutionTicker` into the cognitive agent's tick cycle via `CognitionAvatarAdapter`. CURIOSITY drive modulates tick frequency; circuit breaker state feeds PAD mood signals.

| Class | What it does |
|-------|-------------|
| `EvolutionTickBridge` | `@FunctionalInterface` SPI: `TickTrace tick()`. Consumer binds `EvolutionTicker.tick(caseId, tenancyId, config, EVENT_DRIVEN)` at construction. |
| `CognitiveEvolutionConfig` | Record: `curiosityFloor` (0.3), `curiosityFullEngage` (0.7), `circuitBreakerMoodEnabled` (true). `alwaysTick()` factory for testing. |
| `CircuitBreakerMoodMapper` | Stateful mapper: `TickTrace` circuit breaker gate → `MoodSignal.DirectShift`. Tracks previous blocked state for recovery detection. |
| `CognitiveEvolutionAdapter` | Composition root: queries `DriveOrchestrator.currentDrives()` for CURIOSITY intensity, applies frequency modulation (below floor → skip, above fullEngage → always, between → proportional probability), calls `EvolutionTickBridge.tick()`, feeds `CircuitBreakerMoodMapper` signals to `MoodOrchestrator.record()`. |

## Package: `io.casehub.agency.summarisation`

Unified summarisation framework. Core types live in `casehub-agency-summarisation-api` (zero external deps). Extensions in `casehub-agency` depend on qhorus-api/platform-agent-api.

**Core types (in `casehub-agency-summarisation-api`):**

| Class | What it does |
|-------|-------------|
| `EventLevel` | Record: `(String name, int ordinal)` — identifies a level in the hierarchy |
| `LevelEvent<E>` | Record: `(E payload, long timestamp, EventLevel level, @Nullable String tenancyId)` — typed event at a specific level with multi-tenant support |
| `WindowPolicy` | Record: `(long maxAge, int maxCount)` — dual-trigger windowing. Validates: both >= 0, at least one positive. Factory methods: `ofCount(int)`, `ofAge(long)`, `of(long, int)`. |
| `EventAccumulator<E>` | Thread-safe event buffer. `collect()`, `shouldEmit(now)`, `drain()`, `drainIfReady(now)` (atomic check+drain), `peekBuffer()` (read-only snapshot for `EmissionPolicy` evaluation), `clear()`, `size()`. Synchronized on all public methods. |
| `EventStreamBus<E>` | Predicate-based pub/sub. `subscribe(Predicate, Consumer)`, `publish(LevelEvent)` (synchronous dispatch on caller's thread), `clearSubscriptions()`. CopyOnWriteArrayList-backed — concurrent publish+subscribe safe. |
| `Summariser<IN, OUT>` | `@FunctionalInterface`. `CompletionStage<List<OUT>> summarise(List<LevelEvent<IN>>)`. `ofSync()` factory for deterministic implementations. |
| `StatefulSummariser<IN, OUT, S>` | Extends `Summariser`. `CompletionStage<SummariseResult<OUT, S>> summarise(List<LevelEvent<IN>>, @Nullable S previousState)`. Framework-managed state per partition (keyed by tenancyId). `SummariseResult<OUT, S>` record carries outputs + new state. |
| `ContentSummariser<T, R>` | `@FunctionalInterface` SPI: `CompletionStage<R> summarise(List<T>, @Nullable R previous)`. Generified result type — no qhorus dependency. `asSummariser()` default method bridges to `StatefulSummariser<T, R, R>` for pipeline use with state. |
| `Compactor<E>` | `@FunctionalInterface`. `List<LevelEvent<E>> compact(List<LevelEvent<E>>)`. Optional pre-summarisation compaction (dedup, filtering, supersession). |
| `EmissionPolicy<IN, S>` | `@FunctionalInterface` SPI: `boolean shouldEmit(List<LevelEvent<IN>>, @Nullable S, long)`. State-aware emission gating. Static `anyOf()`/`allOf()` composition factories. |
| `StateStore<S>` | Opt-in persistent state SPI: `load(String partitionKey)` → `@Nullable S`, `store(String partitionKey, S)`. Write-through cache in runners — reads check `ConcurrentHashMap` first, fall back to store. |
| `OutputProcessor<OUT, S>` | `@FunctionalInterface` SPI: `List<OUT> process(List<OUT>, @Nullable S)`. Post-summarisation output processing — called after `summarise()`, before output bus publish. |
| `SummarisationRunner<IN, OUT>` | Wires accumulator → optional compactor → summariser → output bus. Detects `StatefulSummariser` and manages state per partition automatically. `collect()`, `tick()` (synchronized, returns `CompletionStage<Void>`), `clear()`, `size()`. Propagates tenancyId from input batch to output events. Builder API: `.emissionPolicy()` (replaces `WindowPolicy` gating), `.stateStore()` (write-through persistent state), `.stateKeyResolver()` (custom partition key from batch), `.outputProcessor()` (post-summarise processing), `.compactor()`, `.onFailure()`. Legacy `WindowPolicy` constructors unchanged. |
| `KeyedAccumulator<K, E>` | Groups events by key (via `Function<LevelEvent<E>, K>`), emits each group independently on completion predicate or stale timeout. Thread-safe. Key extractor receives full `LevelEvent` (enables tenancyId-based partitioning). |
| `KeyedSummarisationRunner<K, IN, OUT>` | Wires `KeyedAccumulator` → optional compactor → `Summariser` → output bus. Same key extractor signature as `KeyedAccumulator`. Per-group failure recovery. Detects `StatefulSummariser` via `instanceof` — manages per-key state in `ConcurrentHashMap<K, Object>`. `evictState(K)` for explicit state cleanup. Implements `Tickable`. Builder API: `.stateStore()` (write-through with `K.toString()` partition key), `.outputProcessor()`, `.compactor()`, `.onFailure()`. |
| `VerbatimContentSummariser<T>` | Renders each item as a bullet list via `Function<T, String>`. Implements `ContentSummariser<T, String>`. Pure Java. |

**Extension types (in `casehub-agency`):**

| Class | What it does |
|-------|-------------|
| `SummaryMode` | Enum: `APPEND` (delta only) or `EDIT` (rewrite entire summary). Used by `LlmContentSummariser`. |
| `TieredContentSummariser<T>` | Dispatches to `ContentSummariser<T, SummaryResult>` delegates based on batch size thresholds. 2-tier and 3-tier constructors. |

Two integration patterns: **Pattern A** (SummarisationRunner pipeline — sync heuristics, microsecond latency) and **Pattern B** (direct EventAccumulator — async LLM dispatch, caller manages). `KeyedSummarisationRunner` is the grouped counterpart to `SummarisationRunner` — same compositional role, groups by key instead of flat windowing. See spec for details.

## Sub-package: `io.casehub.agency.perception.observation`

Terminal consumer of the summarisation pipeline — tiered, demand-driven rendering for LLM agent prompts with RAG-able chunk production.

| Class | What it does |
|-------|-------------|
| `ObservationTier` | Record: named tier in rendering hierarchy `(String name, int ordinal)`. Mirrors `EventLevel`. Predefined: VERBATIM, GROUPED, SUMMARISED. |
| `ObservationContext` | Record: render-time context `(long currentTime, long timeSinceLastDrain)`. |
| `ObservationChunk` | Record: RAG-able content unit with extensible `Map<String, String>` metadata. Agent-agnostic — consumer adds identity when storing. |
| `ObservationResult` | Record: drain output — `renderedText` + `chunks` + metadata. Factory: `empty(timeSinceLastDrain)`. |
| `ObservationRenderer<E>` | `@FunctionalInterface` SPI: `render(List<LevelEvent<E>>, ObservationContext) → CompletionStage<ObservationResult>`. Stateless and shareable. |
| `TieredObservationRenderer<E>` | Standard implementation: routes to verbatim (≤ threshold), grouped (≤ threshold), or summarised (via `Summariser<E, String>`) based on batch size. Configurable header via `withHeaderFormatter`. Two-tier and three-tier constructors. |
| `ObservationAccumulator<E>` | Thread-safe buffer with demand-driven drain. Own buffer (not `EventAccumulator`). Tracks `lastDrainTimestamp`. Empty drain → `ObservationResult.empty()`. At-most-once delivery on renderer failure. |

## Sub-package: `io.casehub.agency.perception.observation.affordance`

Grounded observation rendering for LLM agents. Per-entity affordance chains (identity + action + consequence) and typed section assembly. Parallel producer to the temporal observation pipeline — consumer concatenates both.

| Class | What it does |
|-------|-------------|
| `ObservableEntity` | Record: entity visible to an agent `(String id, String displayName, @Nullable String description, List<Affordance> affordances)` |
| `Affordance` | Record: action available on an entity `(String actionType, @Nullable String label, @Nullable String requiredItem, List<String> acceptsItems)`. Compositional tag format: `[ACTION label, requires: item, with: items]` |
| `ObservationSection` | Sealed interface: `EntityGroup` (entities with grounding chains), `TextBlock` (contextual prose), `ItemList` (bulleted items). Factory methods: `entities()`, `text()`, `items()` |
| `ActionDescriptor` | Record: action type in the vocabulary `(String actionType, String description, @Nullable String parameterFormat)` |
| `AffordanceRenderer` | Concrete class: `renderEntities()` (core grounding chains), `renderObservation()` (section assembly), `renderActionVocabulary()` (action vocabulary). Configurable header formatter via `withHeaderFormatter()` |
| `WorldObservationProvider` | `@FunctionalInterface` SPI: `List<ObservationSection> worldSections()`. Returns world-specific observation sections (location, exits, objects, characters). Consumers accept a provider instead of a concrete world state, so cognitive sections (goals, plans, memories) become a shared utility. |
| `CognitiveObservationSections` | Static utility: factory methods for cognitive observation sections — `goalsSection(List<AgentGoal>)`, `recentActivitySection(PartitionedDrain)`, `pastExperienceSection(List<Memory>)`, `insightsSection(List<Memory>)`, `relationshipNotesSection(String, List<Memory>)`, `motivationalStateSection(DriveProfile)`, `narrativeSection(NarrativeState)`, `beliefsSection(List<Belief<?>>, Set<String> revisedKeys)`, `principlesSection(List<Principle>)`, `trustSection(List<TrustSummary>)`, `normsSection(List<SocialNorm>)`. Each returns an `ObservationSection`. `Principle` (text + optional category), `TrustLevel` enum (HIGH/MODERATE/LOW/UNKNOWN), `TrustSummary` (subjectName + level + optional reason) — rendering-input records in the same package. |
| `ResolutionTier` | Enum: `FULL`, `REDUCED`, `SUMMARY` — rendering fidelity levels for capability-driven observation filtering |
| `AnnotatedSection` | Record wrapping `ObservationSection` with capability metadata (`requiredTags`, `resolutionAlternatives`, `interpretiveFrame`). Implements `ObservationSection` (sealed permit). |
| `ObservationFilter` | `@FunctionalInterface` SPI: pipeline stage for filtering/transforming observation sections |
| `ObservationPipeline` | Ordered filter stage composition — applies `ObservationFilter` chain, unwraps `AnnotatedSection` to base `ObservationSection` |
| `PerceptionFilter` | Built-in `ObservationFilter` combining visibility gating (tag matching) + resolution fallback (tier degradation when tags partially match) |

## Sub-package: `io.casehub.agency.perception.llm`

LLM-backed content summarisation. Separated from the pure-Java `agency.summarisation` package because it depends on `AgentProvider` (platform-agent-api) and Mutiny.

| Class | What it does |
|-------|-------------|
| `LlmContentSummariser<T>` | Generic `ContentSummariser<T, R>` backed by `AgentProvider`. EDIT/APPEND modes via `SummaryMode`. Optional `preamble` for static context (e.g., channel name). Propagates previous annotations. |

## Sub-package: `io.casehub.agency.perception.narrative`

Decision narrative pipeline — transforms platform decision signals into human-readable explanations via a two-level summarisation pipeline. Architecturally separate from identity narratives (`NarrativeFragment`/`NarrativeOrchestrator` in social cognition). Shares only the underlying summarisation primitives (`KeyedSummarisationRunner`, `EventStreamBus`, `ContentSummariser`).

| Class | What it does |
|-------|-------------|
| `DecisionSignal` | Sealed interface (5 permits): `caseId()`, `stepName()`, `timestamp()`. Pipeline input type. |
| `RoutingDecision` | Record: `selectedAgentId`, `strategyId`, `score`, `candidateIds`, `@Nullable reason` |
| `CbrRetrieval` | Record: `retrievedCount`, `topSimilarity`, `@Nullable topCaseOutcome`, `@Nullable domain` |
| `TrustAssessment` | Record: `agentId`, `trustScore`, `threshold`, `passed` |
| `DeliberationOutcome` | Record: `outcome`, `rounds`, `@Nullable convergenceState`, `participantIds` |
| `StepOutcome` | Record: `status`, `@Nullable workerId`, `@Nullable errorMessage`, `elapsed` |
| `SignalDigest` | Record: `signalType`, `summary`, `keyFacts` (Map), `confidence` — L1→L2 intermediate |
| `StepDecisionSummary` | Record: `caseId`, `stepName`, `signals` (List of SignalDigest), `from`, `to` — L1 output |
| `DecisionNarrative` | Record: `caseId`, `stepNames`, `explanation`, `evidenceSources`, `confidence`, `producedAt` — L2 output |
| `DecisionSignalSummariser` | L1 heuristic `Summariser.SyncSummariser<DecisionSignal, StepDecisionSummary>` — exhaustive sealed switch, no LLM |
| `DecisionNarrativeSummariser` | `@ApplicationScoped` L2 `ContentSummariser<StepDecisionSummary, DecisionNarrative>` — LLM-backed via `AgentProvider` with template fallback |
| `NarrativeSignalStrategy` | SPI: `onStepOutcome(Object event)`. Domain repos implement to convert platform events to `DecisionSignal`. |
| `AbstractNarrativeSignalStrategy` | Base class: `emit(DecisionSignal)` publishes to `EventStreamBus`, `onCaseClose(String)` evicts L2 state |
| `DecisionNarrativePipeline` | `@ApplicationScoped` factory: wires L1 (`KeyedSummarisationRunner` by caseId:stepName) → L2 (`KeyedSummarisationRunner` by caseId with stateful `ContentSummariser`). `tick(long)`, `evictCaseState(String)`. |

## Package: `io.casehub.agency.speech.sherpa`

sherpa-onnx speech implementation via Java FFM/Panama (JDK 22+). Provides `SpeechToTextService` and `TextToSpeechService` implementations backed by sherpa-onnx native library. Zero foundation dependencies — only depends on `casehub-agency-speech-api`.

| Class | What it does |
|-------|-------------|
| `SherpaOnnxSpeechToText` | `SpeechToTextService` implementation — Whisper-based transcription via FFM downcalls. Reads WAV files, feeds PCM samples to sherpa-onnx offline recognizer, returns `TranscriptionResult`. |
| `SherpaOnnxTextToSpeech` | `TextToSpeechService` implementation — VITS/Piper-based synthesis via FFM downcalls. Generates audio from text, encodes to WAV, returns `SynthesisResult`. Auto-detects TTS model filename via `findTtsModel()` directory scan. `withDefaults()`/`withDefaults(modelName)` zero-install factories, `ensureProvisioned(modelName)` for pre-download. No phoneme timing (returns empty list). |
| `KokoroTextToSpeech` | `TextToSpeechService` implementation — Kokoro (StyleTTS2) synthesis via sherpa-onnx FFM downcalls. 24kHz output, 28 voices. `withDefaults()`/`withDefaults(voiceId)` zero-install factories. No phoneme timing (returns empty list). |
| `KokoroConfig` | Record: `modelDir`, `voiceId`, `lengthScale`, `numThreads`, `provider`. `defaults(Path)` and `defaults(Path, voiceId)` factories. |
| `SherpaLibrary` | Native library loading via `SymbolLookup.libraryLookup()`. Resolves all STT and TTS function handles. Singleton with explicit path override. `isAvailable()` for runtime detection. |
| `SherpaLayouts` | `StructLayout` definitions for sherpa-onnx v1.13.6 C API structs — `OFFLINE_RECOGNIZER_CONFIG`, `TTS_CONFIG` (VITS + Kokoro offsets), `GENERATED_AUDIO`. VarHandles for field access. |
| `SherpaConfig` | Record: `modelDir`, `numThreads`, `provider`. `defaults(Path)` factory. |
| `WavData` | Record: `float[] samples`, `sampleRate`, `channels` — decoded PCM audio. |
| `WavReader` | WAV file parser — reads RIFF/WAV headers, extracts 16-bit PCM samples as float[]. |
| `WavWriter` | WAV encoder — encodes float[] samples to 16-bit PCM WAV bytes. |
| `MicrophoneCapture` | Audio capture utility — bridges `javax.sound.sampled.TargetDataLine` to `RecognitionStream.acceptSamples()`. Background platform daemon thread reads 100ms PCM chunks. Injectable `TargetDataLine` for testability, `openDefault()` factory for convenience. |
| `SpeechCli` | CLI entry point — `transcribe` (offline Whisper), `synthesise` (VITS/Piper), `stream` (streaming Zipformer from WAV), `listen` (live microphone streaming STT). |
| `VitsTextToSpeech` | `TextToSpeechService` implementation — direct onnxruntime + espeak-ng TTS with exact per-phoneme timing from VITS duration predictor. Bypasses sherpa-onnx TTS API. Composes `EspeakLibrary` (phonemization) + `VitsConfig` (tokenization with VITS blank interspersing) + `OnnxRuntimeLibrary` (inference). `AutoCloseable` — owns ORT session lifecycle. Graceful degradation for unpatched models. |
| `OnnxRuntimeLibrary` | FFM bindings for onnxruntime C API via vtable pattern (`OrtGetApiBase`). Loads `libonnxruntime` independently (system path → sherpa-onnx cache). Singleton with shared ORT env across sessions. `Session` inner class wraps ORT session with typed inference (`runFloat`, `runRaw`), tensor creation, output count/name queries. |
| `EspeakLibrary` | FFM bindings for espeak-ng C API — `espeak_Initialize`, `espeak_SetVoiceByName`, `espeak_TextToPhonemes`, `espeak_Terminate`. All methods `synchronized` (espeak-ng uses global state). Singleton. |
| `VitsConfig` | Piper VITS model config — parses `.onnx.json` via gson for `phoneme_id_map`, `espeak.voice`, `audio.sample_rate`, inference defaults. Greedy longest-match IPA tokenization with VITS blank token interspersing (2N+1 output). Reverse phoneme ID mapping for duration-to-phoneme conversion. |
| `ModelPatcher` | Static utility — patches ONNX model to expose `/Ceil_output_0` (VITS duration predictor output) as a second model output. Uses protobuf-java `UnknownFieldSet` for schema-free ONNX manipulation. Idempotent. |
| `SentencePieceTokenizer` | Pure Java SentencePiece tokenizer — Viterbi unigram segmentation. Reads `.model` protobuf via `CodedInputStream` (no proto compilation). Trie-based vocabulary lookup, space normalization (▁), consecutive unknown collapsing. `encode(String) → int[]`, `decode(int[]) → String`. |
| `GectorFilter` | `TextFilter` implementation (destructiveness 3, name "grammar"). Composes `WordTokenizer` + `SentencePieceTokenizer` + `OnnxRuntimeLibrary` + `GectorTagApplier` for iterative GECToR grammar correction. Per-word subword encoding, DeBERTa inference, softmax + threshold filtering, first-subword-wins tag aggregation. `AutoCloseable` — owns ORT session. |
| `GectorConfig` | Record: `modelPath`, `spModelPath`, `maxIterations` (default 5), `keepConfidence`, `minErrorProb`, `numThreads`, `provider`, `tagVocabulary`, `keepTagId`, `verbDictionary`. Factory `fromModelDir(Path)` reads `labels.txt` and optional `verb-form-vocab.txt`. |
| `GectorTagApplier` | Stateless utility: `apply(List<String> tokens, int[] tagIds, GectorConfig) → Result(tokens, changed)`. Handles $KEEP, $DELETE, $APPEND_{word}, $REPLACE_{word}, $TRANSFORM_VERB/CASE/AGREEMENT, $MERGE_HYPHEN. Verb transforms fall back to original when not in dictionary. |
| `WordTokenizer` | Rule-based word pre-tokenization matching spaCy English rules. Contraction splitting (n't, 's, 're, 've, 'll, 'd, 'm), punctuation separation. `splitSentences(String)` for sentence boundary detection. |
| `Audio8TextToSpeech` | `TextToSpeechService` implementation — DualAR autoregressive TTS via `OnnxRuntimeLibrary`. 0.1B (INT8) and 0.6B (INT4) model variants. `withDefaults()`/`withDefaults(variant)` zero-install factories, `ensureProvisioned(variant)` for pre-download. `synthesiseStreaming()` with windowed overlap-subtract codec decoding for progressive audio delivery. Voice cloning via `registerVoice(Path)`/`releaseVoice(String)` with transient codec encoder. `AutoCloseable` — owns ONNX session lifecycle. |
| `Audio8Tokenizer` | GPT-2 byte-level BPE tokenizer — parses HuggingFace `tokenizer.json`, byte-to-unicode mapping, greedy longest-match BPE merge. `encode(String) → int[]`, `decode(int[]) → String`. |
| `Audio8Config` | Record: `modelDir`, `variant`, `numThreads`, `provider`, `maxTokens`, `temperature`, `topP`, `topK`. `defaults(Path)` and `defaults(Path, variant)` factories. |
| `DualARLoop` | Autoregressive inference engine — slow AR (semantic token prediction with KV-cache + Mamba SSM state) → fast AR (codec codebook prediction per frame). `generate()` returns all frames, `iterateFrames()` yields frames via `Consumer<int[]>` callback for streaming. `AutoCloseable` — owns slow/fast AR ONNX sessions. |
| `CodecDecoder` | Codec tokens → PCM waveform via ORT. Reshapes frame matrix to `[1, codebooks, frames]` tensor, runs codec decoder session. `AutoCloseable`. |
| `VoiceRegistry` | Voice cloning lifecycle — `register(Path)` transiently loads codec encoder, encodes reference audio to codes, caches under opaque ID. `getVoiceCodes(String)`, `release(String)`, `registeredVoices()`. Thread-safe via `ConcurrentHashMap`. |
| `RuntimeManifest` | Model metadata record — parsed from `runtime_manifest.json`. Architecture params (layers, heads, dims, Mamba state), codec params (codebooks, codebook size, sample rate), streaming params (`codecHopLength`, `streamContextFrames`, `streamGuardFrames`), reference voice (`referenceCodesFile`, `referenceText`). |
| `EspeakPhonemeAligner` | `PhonemeAligner` implementation — espeak-ng phonemization via `Phonemizer` SPI + proportional duration mapping to audio length. `withDefaults()` zero-config factory (provisions espeak-ng automatically), `withEspeak(EspeakLibrary)` for explicit control. |
| `SherpaException` | Unchecked exception for native binding failures. |

## Dependencies

**Compile:** `casehub-qhorus-api`, `casehub-work-api`, `casehub-engine-api`, `casehub-eidos-api`, `casehub-worker-api`, `org.jspecify:jspecify`
**Provided:** `io.smallrye.reactive:mutiny`, `casehub-platform-agent-api`, `casehub-platform-api`, `casehub-coordination-core`, `casehub-engine-ledger`, `casehub-ledger-api`, `casehub-neocortex-memory-api`, `casehub-neocortex-rag-api`, `casehub-work-progress-api`, `io.opentelemetry:opentelemetry-api`
**Test:** `casehub-qhorus`, `casehub-qhorus-testing`, `casehub-engine`, `casehub-engine-testing`, `assertj`, `mockito`, `awaitility`, `io.opentelemetry:opentelemetry-sdk-testing`

**No Jandex index.** agency does not include a Jandex index — its CDI beans are not auto-discovered by Quarkus. Consumers that need agency's CDI beans (routing strategies, channel summarisers) must opt in:
```properties
quarkus.index-dependency.casehub-agency.group-id=io.casehub
quarkus.index-dependency.casehub-agency.artifact-id=casehub-agency
```
Consumers that only use agency's pure types (records, sealed interfaces, plain classes) need no configuration.

**cloudevents module:**
**Compile:** `casehub-agency-summarisation-api`, `io.cloudevents:cloudevents-api`, `io.cloudevents:cloudevents-core`
**Test:** JUnit 5, AssertJ, Mockito

**summarisation-yaml module:**
**Compile:** `casehub-agency-summarisation-api`, `casehub-agency-cloudevents`, `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`
**Provided:** `casehub-platform-api` (ExpressionEngine SPI), `io.quarkus:quarkus-core` (recorder annotation)
**Test:** JUnit 5, AssertJ, `casehub-platform-expression` (MvelExpressionEngine)

**agentic-yaml module:**
**Compile:** `casehub-agency`, `jackson-dataformat-yaml`, `jackson-datatype-jsr310`, `casehub-platform-schema-generator`
**Provided:** `casehub-platform-api` (ExpressionEngine SPI), `io.quarkus:quarkus-core`
**Test:** JUnit 5, AssertJ, `casehub-platform-expression` (MvelExpressionEngine)

**speech-sherpa module:**
**Compile:** `casehub-agency-speech-api`, `org.jspecify:jspecify`, `com.google.code.gson:gson`, `com.google.protobuf:protobuf-java`
**Runtime:** sherpa-onnx native library (`sherpa-onnx-c-api`) — must be on `java.library.path`; `libonnxruntime` (bundled with sherpa-onnx); `libespeak-ng` (optional, for `VitsTextToSpeech` phoneme timing)
**Compiler target:** Java 22 (stable FFM/Panama API)
**Test:** JUnit 5, AssertJ

**speech-ws module:**
**Compile:** `casehub-agency-speech-api`, `com.google.code.gson:gson`, `org.jspecify:jspecify`
**Provided:** `casehub-platform-agent-api`, `quarkus-websockets-next`
**Compiler target:** Java 17
**Test:** JUnit 5, AssertJ, Mockito

## Consumers

| Repo | What it uses |
|------|-------------|
| casehub-drafthouse | Channel + conversation — DebateProtocol delegates to `ConversationConstants`, DebateChannelProjection extends `ConversationProjection`, ReviewChannelProjection uses `ConversationFold`/`ConversationState`, `ChannelAgentDispatcher` subclass with debate-specific error dispatch, `BoundedProjectionDecorator` for round bounding, `ContextTracker` for LLM window tracking |
| casehub-engine | Oversight: `GateOutcome`, `OversightGateService`, `ReactiveOversightGateService` (NoOp impls), `ReactiveActionRiskClassifier`, `RiskDecision`, `ClassificationContext` (handler + health check) |
| casehub-openclaw | Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext`, `GateOutcome` (concrete OversightGateService impl) |
| casehub-aml | Routing: `TrustRoutingPolicyKeys`, `TrustRoutingPolicyResolver`, `DoublePreference`, `IntPreference`. Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-devtown | Routing: `TrustRoutingPolicyKeys`, `TrustRoutingPolicyResolver.collectFloors()`, `DoublePreference`. Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-life | Routing: `TrustRoutingPolicyKeys`, `TrustRoutingPolicyResolver.collectFloors()`, `DoublePreference`. Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-soc | Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-clinical | Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-iot | Oversight: `ActionRiskClassifier`, `RiskClassifier`, `RiskDecision`, `ClassificationContext` |
| casehub-quarkmind | Summarisation: `SummarisationRunner`, `EventStreamBus`, `EventAccumulator`, `WindowPolicy`, `Summariser`, `EventLevel`, `LevelEvent` — SC2 game event hierarchy (L2→L3→L4 pipeline via `SummarisationLifecycle`, `GamePhaseSummariser`, `GameArcSummariser`) |

## Agency Scope Criteria

A pattern belongs in agency if it meets at least one of these criteria:
1. **Needs an LLM in the loop** — the pattern involves LLM invocation, prompt construction, or LLM-driven decision-making
2. **Uses classical AI** — classical planning, Bayesian reasoning, CEP (complex event processing), or similar
3. **Requires integration with foundational platform parts** — the pattern composes across qhorus, engine, work, or eidos APIs in a way that would otherwise be duplicated by every consumer

**What does NOT belong in agency:**
- Small isolated utilities (backoff computation, rate limiters, CloudEvent adapters) → stay in platform or engine
- Pure SPI unifications (e.g. ProvisionerConfigRegistry) → stay in the API module that owns the provisioning lifecycle (engine-api)
- Domain-specific logic that happens to be duplicated but doesn't involve AI or foundational integration

**The test:** if removing the LLM/AI/integration aspect leaves a generic utility, it belongs in platform. If removing the domain-specific aspect leaves a reusable AI-integration pattern, it belongs in agency.

## Trust Routing Architecture

The trust routing system spans four layers — agency owns policy configuration AND AI-powered routing strategies.

| Layer | Owner | What it does |
|-------|-------|-------------|
| Score computation | **ledger** | `TrustScoreRoutingPublisher` computes trust scores from ledger entries and publishes them. The `trust-score-routing` package owns all score payloads and events. |
| Policy configuration | **agency** (routing package) + **engine-api** (`TrustRoutingPolicyProvider` SPI) | `TrustRoutingPolicyKeys` + `TrustRoutingPolicyResolver` provide the shared preference-to-policy loading. Domain repos implement `TrustRoutingPolicyProvider` using these utilities. |
| Classical strategy execution | **engine** | `TrustWeightedAgentStrategy` (engine-ledger) applies trust scores. `SemanticAgentRoutingStrategy` (engine-ai) adds embedding-based re-ranking. Strategies stay where their differentiating dependency lives. |
| AI-powered strategy execution | **agency** (routing.agent package) | `LlmAgentRoutingStrategy` (LLM reasoning) and `CbrAgentRoutingStrategy` (case-based evidence). Both optionally compose with trust classification via `Instance<TrustCandidateClassifier>`. |

Domain repos (aml, devtown, clinical, life, ops) implement `TrustRoutingPolicyProvider` from engine-api — they configure policy parameters, not compute scores or execute routing.

## Consolidation Epic

Epic #28 tracks extraction of shared patterns from domain repos into agency. Each child issue covers a distinct pattern duplicated across 2+ repos.

| # | Title | Scale | Complexity | Ready? | Destination | Migrates from | Downstream consumers |
|---|-------|-------|------------|--------|-------------|---------------|---------------------|
| #17 | Trust routing YAML | M | Med | **Done** | agency | aml, devtown, clinical, life, ops, soc | aml, devtown, clinical, life, ops, soc, fsitrading |
| #22 | Debate channel infrastructure | L | High | **Done** | agency | drafthouse | drafthouse, devtown, clinical, aml, claudony |
| #23 | Oversight gate lifecycle + risk classification | L | High | **Done** | agency | openclaw, engine-api | openclaw, aml, soc, life, devtown, clinical, iot, claudony |
| #24 | Universal pluggable routing strategy | L | High | **Moved → engine#634** | engine | engine, work | engine, work, qhorus, eidos |
| #30 | AI routing strategy impls (trust, LLM, CBR) | M | Med | **Done** | agency | — | engine, domain repos |
| #25 | Worker data coordination (DataExchange/DataChannel) | L | High | **Moved → engine#633** | engine | engine | engine, workers, desiredstate |
| #27 | Layered event summarisation | M | Med | **Done** | agency | quarkmind | quarkmind, iot, aml, clinical |

## Cross-Repo Scanning

To scan all CaseHub repos for shared patterns, use `ide_open_workspace` with the parent directory:
```
ide_open_workspace(path="/Users/mdproctor/claude/casehub")
```
This opens all 26 repos in a single IntelliJ window with full cross-project code intelligence. Use `ide_find_class`, `ide_search_text`, and `ide_find_references` with the workspace `project_path` for cross-repo analysis.

## Cross-Repo Consolidation Commits

When implementing consolidation work (epic #28), commits to peer repos (aml, devtown, life, etc.) on main are expected and approved. This is an exception to the normal "do not commit to peer repos" rule — consolidation by definition spans repos. Always:
- Verify all affected repos are on main before starting
- Install agency to local Maven repo before compiling consumers (`mvn install -DskipTests`)
- Commit each repo separately with meaningful messages tagged with the agency issue (`Refs casehubio/agency#N`)
- Push all repos after all commits succeed

## Extraction Plan

Full extraction plan with prioritisation: [casehubio/parent#310 comment](https://github.com/casehubio/parent/issues/310#issuecomment-4795440229). P1–P5 complete. Consolidation epic #28 tracks remaining extractions.

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting. Complete the pre-draft voice classification (I / we / Claude-named) before generating any prose. Do not show a draft without verifying it against the style guide.

## Project Artifacts

Paths that are project content (not workspace noise). Skills use this to avoid
filtering or dropping commits that touch these paths.

| Path | What it is |
|------|------------|
| `CLAUDE.md` | Project conventions |
| `docs/` | Documentation |

## IntelliJ MCP Routing

One IntelliJ MCP server is available:

- **`mcp__intellij-index__*`** — use this for ALL code intelligence and navigation. Supports auto-opening projects via `project_path` — pass the project path and the plugin opens it automatically. Never ask the user to open a project manually.

`mcp__intellij__*` (built-in JetBrains MCP) is **disabled** due to a memory leak. Do not attempt to use it.

## Development Workflow

Before designing: `superpowers:brainstorming`
Before implementing: `superpowers:test-driven-development`
For all Java work: `java-dev`
Before committing: `superpowers:requesting-code-review`
After implementation: `implementation-doc-sync`

## Work Tracking

Issue tracking: enabled
GitHub repo: casehubio/agency
Changelog: GitHub Releases
