# io.casehub.agency.agentic.termination.ConvergenceTermination

**Package:** `io.casehub.agency.agentic.termination`

**Kind:** `class`

## Fields

### `confidenceThreshold` (`double`)

### `epistemicRule` (`io.casehub.agency.conversation.EpistemicRule`)

### `policy` (`io.casehub.agency.conversation.ConvergencePolicy`)

### `recentWindow` (`int`)

### `stateExtractor` (`java.util.function.Function<T,io.casehub.agency.conversation.ConversationState>`)

### `terminateOn` (`java.util.Set<io.casehub.agency.conversation.ConvergenceState>`)

## Constructors

### `public ConvergenceTermination(java.util.function.Function<T,io.casehub.agency.conversation.ConversationState> stateExtractor, io.casehub.agency.conversation.EpistemicRule epistemicRule, io.casehub.agency.conversation.ConvergencePolicy policy, int recentWindow, double confidenceThreshold, java.util.Set<io.casehub.agency.conversation.ConvergenceState> terminateOn)`

#### Parameters

- `stateExtractor` (`java.util.function.Function<T,io.casehub.agency.conversation.ConversationState>`)
- `epistemicRule` (`io.casehub.agency.conversation.EpistemicRule`)
- `policy` (`io.casehub.agency.conversation.ConvergencePolicy`)
- `recentWindow` (`int`)
- `confidenceThreshold` (`double`)
- `terminateOn` (`java.util.Set<io.casehub.agency.conversation.ConvergenceState>`)

## Methods

### `public Uni<io.casehub.agency.agentic.termination.TerminationDecision> evaluate(io.casehub.agency.agentic.termination.TerminationContext<T> context)`

#### Parameters

- `context` (`io.casehub.agency.agentic.termination.TerminationContext<T>`)

### `private io.casehub.agency.agentic.termination.TerminationDecision toDecision(io.casehub.agency.conversation.ConvergenceSignal signal)`

#### Parameters

- `signal` (`io.casehub.agency.conversation.ConvergenceSignal`)
