# io.casehub.agency.conversation.ConvergencePolicies

**Package:** `io.casehub.agency.conversation`

**Kind:** `class`

## Fields

### `TIEBREAKER` (`java.util.Comparator<io.casehub.agency.conversation.ConvergenceSignal>`)

## Constructors

### `private ConvergencePolicies()`

## Methods

### `public static io.casehub.agency.conversation.ConvergencePolicy commonGroundRatio(double consensusThreshold, double deadlockDisputeRatio)`

#### Parameters

- `consensusThreshold` (`double`)
- `deadlockDisputeRatio` (`double`)

### `public static io.casehub.agency.conversation.ConvergencePolicy composite(io.casehub.agency.conversation.ConvergencePolicy[] policies)`

#### Parameters

- `policies` (`io.casehub.agency.conversation.ConvergencePolicy[]`)

### `private static int severity(io.casehub.agency.conversation.ConvergenceState state)`

#### Parameters

- `state` (`io.casehub.agency.conversation.ConvergenceState`)

### `public static io.casehub.agency.conversation.ConvergencePolicy structural(double similarityThreshold, int staleRounds)`

#### Parameters

- `similarityThreshold` (`double`)
- `staleRounds` (`int`)
