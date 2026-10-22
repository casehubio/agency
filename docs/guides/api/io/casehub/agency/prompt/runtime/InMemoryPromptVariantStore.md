# io.casehub.agency.optimisation.prompt.runtime.InMemoryPromptVariantStore

**Package:** `io.casehub.agency.optimisation.prompt.runtime`

**Kind:** `class`

## Fields

### `activeSlots` (`java.util.Map<java.lang.String,java.util.Map<java.lang.String,io.casehub.agency.optimisation.prompt.PromptVariant>>`)

### `history` (`java.util.Map<java.lang.String,java.util.List<io.casehub.agency.optimisation.prompt.PromptVariant>>`)

## Constructors

### `public InMemoryPromptVariantStore()`

## Methods

### `public void activate(java.lang.String signatureId, java.lang.String variantId, java.lang.String variantSlot)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `variantId` (`java.lang.String`)
- `variantSlot` (`java.lang.String`)

### `public io.casehub.agency.optimisation.prompt.PromptVariant getActive(java.lang.String signatureId, java.lang.String variantSlot)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `variantSlot` (`java.lang.String`)

### `public java.util.List<io.casehub.agency.optimisation.prompt.PromptVariant> getHistory(java.lang.String signatureId, int limit)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `limit` (`int`)

### `public void store(io.casehub.agency.optimisation.prompt.PromptVariant variant)`

#### Parameters

- `variant` (`io.casehub.agency.optimisation.prompt.PromptVariant`)
