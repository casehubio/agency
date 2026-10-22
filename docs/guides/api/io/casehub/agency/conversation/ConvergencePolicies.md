# io.casehub.agency.deliberation.conversation.ConvergencePolicies

**Package:** `io.casehub.agency.deliberation.conversation`

**Kind:** `class`

## Fields

### `TIEBREAKER` (`java.util.Comparator<io.casehub.agency.deliberation.conversation.ConvergenceSignal>`)

## Constructors

### `private ConvergencePolicies()`

## Methods

### `public static io.casehub.agency.deliberation.conversation.ConvergencePolicy commonGroundRatio(double consensusThreshold, double deadlockDisputeRatio)`

#### Parameters

- `consensusThreshold` (`double`)
- `deadlockDisputeRatio` (`double`)

### `public static io.casehub.agency.deliberation.conversation.ConvergencePolicy composite(io.casehub.agency.deliberation.conversation.ConvergencePolicy[] policies)`

#### Parameters

- `policies` (`io.casehub.agency.deliberation.conversation.ConvergencePolicy[]`)

### `private static int severity(io.casehub.agency.deliberation.conversation.ConvergenceState state)`

#### Parameters

- `state` (`io.casehub.agency.deliberation.conversation.ConvergenceState`)

### `public static io.casehub.agency.deliberation.conversation.ConvergencePolicy structural(double similarityThreshold, int staleRounds)`

#### Parameters

- `similarityThreshold` (`double`)
- `staleRounds` (`int`)
