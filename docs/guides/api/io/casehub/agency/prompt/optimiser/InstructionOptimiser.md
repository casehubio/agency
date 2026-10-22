# io.casehub.agency.optimisation.prompt.optimiser.InstructionOptimiser

**Package:** `io.casehub.agency.optimisation.prompt.optimiser`

**Kind:** `class`

## Fields

### `LOG` (`java.lang.System.Logger`)

### `META_SYSTEM_PROMPT` (`java.lang.String`)

### `agentProvider` (`AgentProvider`)

## Constructors

### `public InstructionOptimiser(AgentProvider agentProvider)`

#### Parameters

- `agentProvider` (`AgentProvider`)

## Methods

### `private java.lang.String buildMetaPrompt(io.casehub.agency.optimisation.prompt.PromptSignature signature, java.util.List<io.casehub.agency.optimisation.prompt.VariantOutcome> outcomes)`

#### Parameters

- `signature` (`io.casehub.agency.optimisation.prompt.PromptSignature`)
- `outcomes` (`java.util.List<io.casehub.agency.optimisation.prompt.VariantOutcome>`)

### `public java.lang.String id()`

### `public java.util.concurrent.CompletionStage<io.casehub.agency.optimisation.prompt.OptimiserResult> optimise(io.casehub.agency.optimisation.prompt.PromptSignature signature, io.casehub.agency.optimisation.prompt.PromptVariant currentVariant, io.casehub.agency.optimisation.prompt.OptimisationDataset dataset, io.casehub.agency.optimisation.prompt.OptimiserConfig config)`

#### Parameters

- `signature` (`io.casehub.agency.optimisation.prompt.PromptSignature`)
- `currentVariant` (`io.casehub.agency.optimisation.prompt.PromptVariant`)
- `dataset` (`io.casehub.agency.optimisation.prompt.OptimisationDataset`)
- `config` (`io.casehub.agency.optimisation.prompt.OptimiserConfig`)
