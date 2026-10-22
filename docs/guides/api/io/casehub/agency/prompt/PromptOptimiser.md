# io.casehub.agency.prompt.PromptOptimiser

**Package:** `io.casehub.agency.prompt`

**Kind:** `interface`

## Methods

### `public abstract java.lang.String id()`

### `public abstract java.util.concurrent.CompletionStage<io.casehub.agency.prompt.OptimiserResult> optimise(io.casehub.agency.prompt.PromptSignature signature, io.casehub.agency.prompt.PromptVariant currentVariant, io.casehub.agency.prompt.OptimisationDataset dataset, io.casehub.agency.prompt.OptimiserConfig config)`

#### Parameters

- `signature` (`io.casehub.agency.prompt.PromptSignature`)
- `currentVariant` (`io.casehub.agency.prompt.PromptVariant`)
- `dataset` (`io.casehub.agency.prompt.OptimisationDataset`)
- `config` (`io.casehub.agency.prompt.OptimiserConfig`)
