# io.casehub.agency.perception.observation.ObservationAccumulator

**Package:** `io.casehub.agency.perception.observation`

**Kind:** `class`

## Fields

### `buffer` (`java.util.List<io.casehub.agency.summarisation.LevelEvent<E>>`)

### `lastDrainTimestamp` (`long`)

### `renderer` (`io.casehub.agency.perception.observation.ObservationRenderer<E>`)

## Constructors

### `public ObservationAccumulator(io.casehub.agency.perception.observation.ObservationRenderer<E> renderer)`

#### Parameters

- `renderer` (`io.casehub.agency.perception.observation.ObservationRenderer<E>`)

### `ObservationAccumulator(io.casehub.agency.perception.observation.ObservationRenderer<E> renderer, long createdAt)`

#### Parameters

- `renderer` (`io.casehub.agency.perception.observation.ObservationRenderer<E>`)
- `createdAt` (`long`)

## Methods

### `public synchronized void clear()`

### `public synchronized void collect(io.casehub.agency.summarisation.LevelEvent<E> event)`

#### Parameters

- `event` (`io.casehub.agency.summarisation.LevelEvent<E>`)

### `public java.util.concurrent.CompletionStage<io.casehub.agency.perception.observation.ObservationResult> drainObservation(long now)`

#### Parameters

- `now` (`long`)

### `public synchronized int eventCount()`
