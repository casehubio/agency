# io.casehub.agency.prompt.optimiser.InstructionOptimiser

**Package:** `io.casehub.agency.prompt.optimiser`

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

### `private java.lang.String buildMetaPrompt(io.casehub.agency.prompt.PromptSignature signature, java.util.List<io.casehub.agency.prompt.VariantOutcome> outcomes)`

#### Parameters

- `signature` (`io.casehub.agency.prompt.PromptSignature`)
- `outcomes` (`java.util.List<io.casehub.agency.prompt.VariantOutcome>`)

### `public java.lang.String id()`

### `public java.util.concurrent.CompletionStage<io.casehub.agency.prompt.OptimiserResult> optimise(io.casehub.agency.prompt.PromptSignature signature, io.casehub.agency.prompt.PromptVariant currentVariant, io.casehub.agency.prompt.OptimisationDataset dataset, io.casehub.agency.prompt.OptimiserConfig config)`

#### Parameters

- `signature` (`io.casehub.agency.prompt.PromptSignature`)
- `currentVariant` (`io.casehub.agency.prompt.PromptVariant`)
- `dataset` (`io.casehub.agency.prompt.OptimisationDataset`)
- `config` (`io.casehub.agency.prompt.OptimiserConfig`)
