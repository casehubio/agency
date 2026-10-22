# io.casehub.agency.optimisation.prompt.runtime.OptimisedFewShotSection

**Package:** `io.casehub.agency.optimisation.prompt.runtime`

**Kind:** `class`

## Fields

### `selector` (`io.casehub.agency.optimisation.prompt.VariantSelector`)

### `signatureId` (`java.lang.String`)

### `store` (`io.casehub.agency.optimisation.prompt.PromptVariantStore`)

## Constructors

### `public OptimisedFewShotSection(io.casehub.agency.optimisation.prompt.PromptVariantStore store, io.casehub.agency.optimisation.prompt.VariantSelector selector, java.lang.String signatureId)`

#### Parameters

- `store` (`io.casehub.agency.optimisation.prompt.PromptVariantStore`)
- `selector` (`io.casehub.agency.optimisation.prompt.VariantSelector`)
- `signatureId` (`java.lang.String`)

## Methods

### `private java.lang.String formatExamples(java.util.List<io.casehub.agency.optimisation.prompt.FewShotExample> examples)`

#### Parameters

- `examples` (`java.util.List<io.casehub.agency.optimisation.prompt.FewShotExample>`)

### `public java.lang.String render(AgentRoutingContext context, java.util.List<AgentCandidate> eligible)`

#### Parameters

- `context` (`AgentRoutingContext`)
- `eligible` (`java.util.List<AgentCandidate>`)
