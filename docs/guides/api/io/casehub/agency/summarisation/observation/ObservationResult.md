# io.casehub.agency.summarisation.observation.ObservationResult

**Package:** `io.casehub.agency.summarisation.observation`

**Kind:** `record`

## Fields

### `chunks` (`java.util.List<io.casehub.agency.summarisation.observation.ObservationChunk>`)

### `eventCount` (`int`)

### `renderedText` (`java.lang.String`)

### `tier` (`io.casehub.agency.summarisation.observation.ObservationTier`)

### `timeSinceLastDrain` (`long`)

## Record Components

### `chunks` (`java.util.List<io.casehub.agency.summarisation.observation.ObservationChunk>`)

### `eventCount` (`int`)

### `renderedText` (`java.lang.String`)

### `tier` (`io.casehub.agency.summarisation.observation.ObservationTier`)

### `timeSinceLastDrain` (`long`)

## Constructors

### `public ObservationResult(java.lang.String renderedText, java.util.List<io.casehub.agency.summarisation.observation.ObservationChunk> chunks, int eventCount, long timeSinceLastDrain, io.casehub.agency.summarisation.observation.ObservationTier tier)`

#### Parameters

- `renderedText` (`java.lang.String`)
- `chunks` (`java.util.List<io.casehub.agency.summarisation.observation.ObservationChunk>`)
- `eventCount` (`int`)
- `timeSinceLastDrain` (`long`)
- `tier` (`io.casehub.agency.summarisation.observation.ObservationTier`)

## Methods

### `public java.util.List<io.casehub.agency.summarisation.observation.ObservationChunk> chunks()`

### `public static io.casehub.agency.summarisation.observation.ObservationResult empty(long timeSinceLastDrain)`

#### Parameters

- `timeSinceLastDrain` (`long`)

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public int eventCount()`

### `public final int hashCode()`

### `public java.lang.String renderedText()`

### `public io.casehub.agency.summarisation.observation.ObservationTier tier()`

### `public long timeSinceLastDrain()`

### `public final java.lang.String toString()`
