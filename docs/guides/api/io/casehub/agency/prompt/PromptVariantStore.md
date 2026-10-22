# io.casehub.agency.prompt.PromptVariantStore

**Package:** `io.casehub.agency.prompt`

**Kind:** `interface`

## Methods

### `public abstract void activate(java.lang.String signatureId, java.lang.String variantId, java.lang.String variantSlot)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `variantId` (`java.lang.String`)
- `variantSlot` (`java.lang.String`)

### `public abstract io.casehub.agency.prompt.PromptVariant getActive(java.lang.String signatureId, java.lang.String variantSlot)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `variantSlot` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.agency.prompt.PromptVariant> getHistory(java.lang.String signatureId, int limit)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `limit` (`int`)

### `public abstract void store(io.casehub.agency.prompt.PromptVariant variant)`

#### Parameters

- `variant` (`io.casehub.agency.prompt.PromptVariant`)
