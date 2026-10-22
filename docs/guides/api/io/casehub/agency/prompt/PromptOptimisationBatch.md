# io.casehub.agency.prompt.PromptOptimisationBatch

**Package:** `io.casehub.agency.prompt`

**Kind:** `class`

## Fields

### `DEFAULT_MIN_PROMOTION_CYCLES` (`int`)

### `DEFAULT_PROMOTION_MARGIN` (`double`)

### `LOG` (`java.lang.System.Logger`)

### `metric` (`io.casehub.agency.prompt.PromptQualityMetric`)

### `optimisers` (`java.util.List<io.casehub.agency.prompt.PromptOptimiser>`)

### `runningSignatures` (`java.util.Set<java.lang.String>`)

### `safetyConfig` (`io.casehub.agency.prompt.SafetyConfig`)

### `store` (`io.casehub.agency.prompt.PromptVariantStore`)

## Constructors

### `public PromptOptimisationBatch(java.util.List<io.casehub.agency.prompt.PromptOptimiser> optimisers, io.casehub.agency.prompt.PromptQualityMetric metric, io.casehub.agency.prompt.PromptVariantStore store, io.casehub.agency.prompt.SafetyConfig safetyConfig)`

#### Parameters

- `optimisers` (`java.util.List<io.casehub.agency.prompt.PromptOptimiser>`)
- `metric` (`io.casehub.agency.prompt.PromptQualityMetric`)
- `store` (`io.casehub.agency.prompt.PromptVariantStore`)
- `safetyConfig` (`io.casehub.agency.prompt.SafetyConfig`)

## Methods

### `private java.util.concurrent.CompletionStage<io.casehub.agency.prompt.BatchResult> doRun(io.casehub.agency.prompt.PromptSignature signature, io.casehub.agency.prompt.OptimisationDataset dataset, io.casehub.agency.prompt.OptimiserConfig config)`

#### Parameters

- `signature` (`io.casehub.agency.prompt.PromptSignature`)
- `dataset` (`io.casehub.agency.prompt.OptimisationDataset`)
- `config` (`io.casehub.agency.prompt.OptimiserConfig`)

### `public java.util.concurrent.CompletionStage<io.casehub.agency.prompt.BatchResult> run(io.casehub.agency.prompt.PromptSignature signature, io.casehub.agency.prompt.OptimisationDataset dataset, io.casehub.agency.prompt.OptimiserConfig config)`

#### Parameters

- `signature` (`io.casehub.agency.prompt.PromptSignature`)
- `dataset` (`io.casehub.agency.prompt.OptimisationDataset`)
- `config` (`io.casehub.agency.prompt.OptimiserConfig`)

### `private java.util.concurrent.CompletionStage<io.casehub.agency.prompt.@Nullable PromptVariant> runOptimisersAndCreateVariant(io.casehub.agency.prompt.PromptSignature signature, io.casehub.agency.prompt.OptimisationDataset dataset, io.casehub.agency.prompt.OptimiserConfig config, io.casehub.agency.prompt.PromptVariant parent)`

#### Parameters

- `signature` (`io.casehub.agency.prompt.PromptSignature`)
- `dataset` (`io.casehub.agency.prompt.OptimisationDataset`)
- `config` (`io.casehub.agency.prompt.OptimiserConfig`)
- `parent` (`io.casehub.agency.prompt.PromptVariant`)

### `private double scoreVariant(io.casehub.agency.prompt.PromptVariant variant, java.util.Map<java.lang.String,java.util.List<io.casehub.agency.prompt.VariantOutcome>> byVariant)`

#### Parameters

- `variant` (`io.casehub.agency.prompt.PromptVariant`)
- `byVariant` (`java.util.Map<java.lang.String,java.util.List<io.casehub.agency.prompt.VariantOutcome>>`)
