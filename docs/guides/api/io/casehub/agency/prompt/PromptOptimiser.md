# io.casehub.agency.optimisation.prompt.PromptOptimiser

**Package:** `io.casehub.agency.optimisation.prompt`

**Kind:** `interface`

## Methods

### `public abstract java.lang.String id()`

### `public abstract java.util.concurrent.CompletionStage<io.casehub.agency.optimisation.prompt.OptimiserResult> optimise(io.casehub.agency.optimisation.prompt.PromptSignature signature, io.casehub.agency.optimisation.prompt.PromptVariant currentVariant, io.casehub.agency.optimisation.prompt.OptimisationDataset dataset, io.casehub.agency.optimisation.prompt.OptimiserConfig config)`

#### Parameters

- `signature` (`io.casehub.agency.optimisation.prompt.PromptSignature`)
- `currentVariant` (`io.casehub.agency.optimisation.prompt.PromptVariant`)
- `dataset` (`io.casehub.agency.optimisation.prompt.OptimisationDataset`)
- `config` (`io.casehub.agency.optimisation.prompt.OptimiserConfig`)
