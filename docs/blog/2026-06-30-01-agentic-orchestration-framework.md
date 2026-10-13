---
title: "Compositional Orchestration — Why CaseHub Doesn't Need a Planner Interface"
date: 2026-06-30
type: phase-update
projects: [casehub-blocks]
tags: [agentic, orchestration, langchain4j, architecture]
---

LangChain4j shipped eleven agentic patterns in the last month. Supervisor, GOAP, HTN, Blackboard, Debate, Voting, P2P — all implementing one interface: `Planner`. One method: `nextAction()`. Clean, elegant, and wrong for what CaseHub needs.

The problem is the assumption baked into that interface. `Planner` says: there is a coordinator, and it decides what happens next. That works when agents are JVM-local functions sharing a key-value store. It falls apart when your agents are distributed workers provisioned via Docker, human reviewers with SLA deadlines, or channel-based message exchanges with speech act semantics. CaseHub has all of these — and a choreographic execution model where agents fire reactively on state changes, with no coordinator at all.

I wanted something that could build any of those eleven patterns — and the five more that langchain4j doesn't have — from the same set of primitives.

## Five concerns, not one interface

Every execution model I could find decomposes into five independent decisions: which agent handles the task (routing), how tasks break into subtasks (decomposition), when agents fire (activation), how results combine (aggregation), and when the work is done (termination). A Supervisor is LLM-selected routing with pass-through aggregation and goal-based termination. A Debate is round-robin routing with convergence-check aggregation and judge-decides termination. Same five knobs, different settings.

Each concern became an SPI. All five return `Uni<>` — uniform reactivity, because any of them might need an LLM call, an embedding lookup, or a database query. The SPIs are generic over `<T>`, ready for engine's `ContextBridge<T>` which makes the shared state a user domain class instead of raw `CaseContext`.

## The platform advantage

What makes this more than an academic decomposition is what sits underneath. The routing SPI can delegate to engine's `TrustWeightedAgentStrategy` for workers that have earned trust through prior executions. It can filter agents by Eidos capability health — is this agent `Ready`, `Degraded`, or `EpistemicallyWeak` for this task domain? It can match on Belbin team roles or DISC personality types via the vocabulary registry.

The aggregation SPI maps to qhorus COLLECT and BARRIER channel semantics for bid collection. The termination SPI generalises engine's Goal model. The activation SPI generalises binding trigger conditions. None of this is reimplemented — blocks composes the existing platform APIs across module boundaries that no single foundation module can cross.

An `AgentRef` in the DSL can be an engine worker, a qhorus channel agent, a human work item, an external function, or a nested execution model. Mix them in the same composition:

```java
supervisor(chatModel)
    .agents(
        worker(reviewerWorker),
        worker(implementorWorker),
        human(WorkItemCreateRequest.builder()
            .title("Arbitration needed")
            .candidateGroups("senior-reviewers")
            .build())
    )
    .build()
```

That's a supervisor where two AI agents handle most tasks but a human arbitrator is available for escalation — all in the same execution model, same driver, same termination condition.

## DSL design

I wrote a DSL Style Guide for the platform that codifies three influences: langchain4j's pattern-named builders, Quarkus Flow's FuncDSL task vocabulary, and CaseHub's existing three-way expression overloads (JQ string, typed predicate, evaluator instance). The blocks DSL follows all three.

Pre-composed builders for the 80% case — `supervisor()`, `debate()`, `voting()`, `htn()` — each with opinionated defaults you can override. Compositional builders for custom patterns — `orchestration()` and `choreography()` — where you wire each concern explicitly. Both return `ExecutionModel<T>`, which you hand to a driver.

## What's next

The framework has the SPI surface and two drivers (orchestrated and choreographed). The pre-composed builders work. What it doesn't have yet is the advanced SPI implementations — LLM-selected routing, GOAP dependency graphs, disposition-aware personality matching, convergence-based termination. Those are the next phase, driven by the execution model issues filed on engine (#596–#606).

`ContextBridge<T>` is landing in engine imminently. When it does, the generic `<T>` on every SPI activates — typed domain records, `AgenticScope`, Drools `WorkingMemory` as first-class context types without retrofit.
