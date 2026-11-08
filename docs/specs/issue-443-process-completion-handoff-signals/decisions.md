## D1: Convergence integration strategy

**Choice:** Dual-path — keep evaluator unchanged, topology tracks all-complete separately
**Alternatives:**
- Extended evaluator — adds completion to CrossSwarmConvergenceEvaluator interface but requires cross-repo engine-api change
- Synthetic progress — injects fake SwarmProgress with consensusScore=1.0, avoids interface changes but conflates completion with consensus
**Rationale:** Keeps the engine-api interface stable. Completion-based convergence is orthogonal to progress-based convergence — they answer different questions (did everyone finish vs. are they agreeing). Separating them makes each path simpler and testable.
**Trade-offs:** Consumer must check two things (evaluator result + isAllComplete) instead of one unified signal. Acceptable because the two paths have different semantics.
**Sources:** MultiSwarmTopology.java:195-219 (processL1Progress), CrossSwarmConvergenceEvaluator.java (engine-api interface)
**Exploration:** quick
**Status:** captured
