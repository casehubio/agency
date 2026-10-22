# io.casehub.agency.orchestration.termination.ConvergenceTermination

**Package:** `io.casehub.agency.orchestration.termination`

**Kind:** `class`

## Fields

### `confidenceThreshold` (`double`)

### `epistemicRule` (`io.casehub.agency.deliberation.conversation.EpistemicRule`)

### `policy` (`io.casehub.agency.deliberation.conversation.ConvergencePolicy`)

### `recentWindow` (`int`)

### `stateExtractor` (`java.util.function.Function<T,io.casehub.agency.deliberation.conversation.ConversationState>`)

### `terminateOn` (`java.util.Set<io.casehub.agency.deliberation.conversation.ConvergenceState>`)

## Constructors

### `public ConvergenceTermination(java.util.function.Function<T,io.casehub.agency.deliberation.conversation.ConversationState> stateExtractor, io.casehub.agency.deliberation.conversation.EpistemicRule epistemicRule, io.casehub.agency.deliberation.conversation.ConvergencePolicy policy, int recentWindow, double confidenceThreshold, java.util.Set<io.casehub.agency.deliberation.conversation.ConvergenceState> terminateOn)`

#### Parameters

- `stateExtractor` (`java.util.function.Function<T,io.casehub.agency.deliberation.conversation.ConversationState>`)
- `epistemicRule` (`io.casehub.agency.deliberation.conversation.EpistemicRule`)
- `policy` (`io.casehub.agency.deliberation.conversation.ConvergencePolicy`)
- `recentWindow` (`int`)
- `confidenceThreshold` (`double`)
- `terminateOn` (`java.util.Set<io.casehub.agency.deliberation.conversation.ConvergenceState>`)

## Methods

### `public Uni<io.casehub.agency.orchestration.termination.TerminationDecision> evaluate(io.casehub.agency.orchestration.termination.TerminationContext<T> context)`

#### Parameters

- `context` (`io.casehub.agency.orchestration.termination.TerminationContext<T>`)

### `private io.casehub.agency.orchestration.termination.TerminationDecision toDecision(io.casehub.agency.deliberation.conversation.ConvergenceSignal signal)`

#### Parameters

- `signal` (`io.casehub.agency.deliberation.conversation.ConvergenceSignal`)
