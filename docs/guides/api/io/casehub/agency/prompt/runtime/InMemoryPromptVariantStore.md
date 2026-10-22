# io.casehub.agency.prompt.runtime.InMemoryPromptVariantStore

**Package:** `io.casehub.agency.prompt.runtime`

**Kind:** `class`

## Fields

### `activeSlots` (`java.util.Map<java.lang.String,java.util.Map<java.lang.String,io.casehub.agency.prompt.PromptVariant>>`)

### `history` (`java.util.Map<java.lang.String,java.util.List<io.casehub.agency.prompt.PromptVariant>>`)

## Constructors

### `public InMemoryPromptVariantStore()`

## Methods

### `public void activate(java.lang.String signatureId, java.lang.String variantId, java.lang.String variantSlot)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `variantId` (`java.lang.String`)
- `variantSlot` (`java.lang.String`)

### `public io.casehub.agency.prompt.PromptVariant getActive(java.lang.String signatureId, java.lang.String variantSlot)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `variantSlot` (`java.lang.String`)

### `public java.util.List<io.casehub.agency.prompt.PromptVariant> getHistory(java.lang.String signatureId, int limit)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `limit` (`int`)

### `public void store(io.casehub.agency.prompt.PromptVariant variant)`

#### Parameters

- `variant` (`io.casehub.agency.prompt.PromptVariant`)
