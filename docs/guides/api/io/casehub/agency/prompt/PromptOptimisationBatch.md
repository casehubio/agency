# io.casehub.agency.optimisation.prompt.PromptOptimisationBatch

**Package:** `io.casehub.agency.optimisation.prompt`

**Kind:** `class`

## Fields

### `DEFAULT_MIN_PROMOTION_CYCLES` (`int`)

### `DEFAULT_PROMOTION_MARGIN` (`double`)

### `LOG` (`java.lang.System.Logger`)

### `metric` (`io.casehub.agency.optimisation.prompt.PromptQualityMetric`)

### `optimisers` (`java.util.List<io.casehub.agency.optimisation.prompt.PromptOptimiser>`)

### `runningSignatures` (`java.util.Set<java.lang.String>`)

### `safetyConfig` (`io.casehub.agency.optimisation.prompt.SafetyConfig`)

### `store` (`io.casehub.agency.optimisation.prompt.PromptVariantStore`)

## Constructors

### `public PromptOptimisationBatch(java.util.List<io.casehub.agency.optimisation.prompt.PromptOptimiser> optimisers, io.casehub.agency.optimisation.prompt.PromptQualityMetric metric, io.casehub.agency.optimisation.prompt.PromptVariantStore store, io.casehub.agency.optimisation.prompt.SafetyConfig safetyConfig)`

#### Parameters

- `optimisers` (`java.util.List<io.casehub.agency.optimisation.prompt.PromptOptimiser>`)
- `metric` (`io.casehub.agency.optimisation.prompt.PromptQualityMetric`)
- `store` (`io.casehub.agency.optimisation.prompt.PromptVariantStore`)
- `safetyConfig` (`io.casehub.agency.optimisation.prompt.SafetyConfig`)

## Methods

### `private java.util.concurrent.CompletionStage<io.casehub.agency.optimisation.prompt.BatchResult> doRun(io.casehub.agency.optimisation.prompt.PromptSignature signature, io.casehub.agency.optimisation.prompt.OptimisationDataset dataset, io.casehub.agency.optimisation.prompt.OptimiserConfig config)`

#### Parameters

- `signature` (`io.casehub.agency.optimisation.prompt.PromptSignature`)
- `dataset` (`io.casehub.agency.optimisation.prompt.OptimisationDataset`)
- `config` (`io.casehub.agency.optimisation.prompt.OptimiserConfig`)

### `public java.util.concurrent.CompletionStage<io.casehub.agency.optimisation.prompt.BatchResult> run(io.casehub.agency.optimisation.prompt.PromptSignature signature, io.casehub.agency.optimisation.prompt.OptimisationDataset dataset, io.casehub.agency.optimisation.prompt.OptimiserConfig config)`

#### Parameters

- `signature` (`io.casehub.agency.optimisation.prompt.PromptSignature`)
- `dataset` (`io.casehub.agency.optimisation.prompt.OptimisationDataset`)
- `config` (`io.casehub.agency.optimisation.prompt.OptimiserConfig`)

### `private java.util.concurrent.CompletionStage<io.casehub.agency.optimisation.prompt.@Nullable PromptVariant> runOptimisersAndCreateVariant(io.casehub.agency.optimisation.prompt.PromptSignature signature, io.casehub.agency.optimisation.prompt.OptimisationDataset dataset, io.casehub.agency.optimisation.prompt.OptimiserConfig config, io.casehub.agency.optimisation.prompt.PromptVariant parent)`

#### Parameters

- `signature` (`io.casehub.agency.optimisation.prompt.PromptSignature`)
- `dataset` (`io.casehub.agency.optimisation.prompt.OptimisationDataset`)
- `config` (`io.casehub.agency.optimisation.prompt.OptimiserConfig`)
- `parent` (`io.casehub.agency.optimisation.prompt.PromptVariant`)

### `private double scoreVariant(io.casehub.agency.optimisation.prompt.PromptVariant variant, java.util.Map<java.lang.String,java.util.List<io.casehub.agency.optimisation.prompt.VariantOutcome>> byVariant)`

#### Parameters

- `variant` (`io.casehub.agency.optimisation.prompt.PromptVariant`)
- `byVariant` (`java.util.Map<java.lang.String,java.util.List<io.casehub.agency.optimisation.prompt.VariantOutcome>>`)
