# io.casehub.agency.prompt.PromptVariant

**Package:** `io.casehub.agency.prompt`

**Kind:** `record`

## Fields

### `consecutiveWins` (`int`)

### `createdAt` (`java.time.Instant`)

### `examples` (`java.util.List<io.casehub.agency.prompt.FewShotExample>`)

### `instructionDelta` (`java.lang.String`)

### `parentVariantId` (`java.lang.String`)

### `qualityScore` (`double`)

### `signatureId` (`java.lang.String`)

### `variantId` (`java.lang.String`)

## Record Components

### `consecutiveWins` (`int`)

### `createdAt` (`java.time.Instant`)

### `examples` (`java.util.List<io.casehub.agency.prompt.FewShotExample>`)

### `instructionDelta` (`java.lang.String`)

### `parentVariantId` (`java.lang.String`)

### `qualityScore` (`double`)

### `signatureId` (`java.lang.String`)

### `variantId` (`java.lang.String`)

## Constructors

### `public PromptVariant(java.lang.String signatureId, java.lang.String variantId, java.util.List<io.casehub.agency.prompt.FewShotExample> examples, java.lang.String instructionDelta, double qualityScore, java.time.Instant createdAt, java.lang.String parentVariantId, int consecutiveWins)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `variantId` (`java.lang.String`)
- `examples` (`java.util.List<io.casehub.agency.prompt.FewShotExample>`)
- `instructionDelta` (`java.lang.String`)
- `qualityScore` (`double`)
- `createdAt` (`java.time.Instant`)
- `parentVariantId` (`java.lang.String`)
- `consecutiveWins` (`int`)

## Methods

### `public int consecutiveWins()`

### `public java.time.Instant createdAt()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.List<io.casehub.agency.prompt.FewShotExample> examples()`

### `public final int hashCode()`

### `public java.lang.String instructionDelta()`

### `public java.lang.String parentVariantId()`

### `public double qualityScore()`

### `public java.lang.String signatureId()`

### `public final java.lang.String toString()`

### `public java.lang.String variantId()`
