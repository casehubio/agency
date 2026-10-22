# io.casehub.agency.summarisation.EventAccumulator

**Package:** `io.casehub.agency.summarisation`

**Kind:** `class`

Thread-safe event buffer. All public methods are synchronized — safe for
concurrent collect() (from async bus callbacks) and tick()-driven
shouldEmit()/drain() on the main thread.

## Fields

### `buffer` (`java.util.List<io.casehub.agency.summarisation.LevelEvent<E>>`)

### `policy` (`io.casehub.agency.summarisation.WindowPolicy`)

## Constructors

### `public EventAccumulator(io.casehub.agency.summarisation.WindowPolicy policy)`

#### Parameters

- `policy` (`io.casehub.agency.summarisation.WindowPolicy`)

## Methods

### `public synchronized void clear()`

### `public synchronized void collect(io.casehub.agency.summarisation.LevelEvent<E> event)`

#### Parameters

- `event` (`io.casehub.agency.summarisation.LevelEvent<E>`)

### `public synchronized java.util.List<io.casehub.agency.summarisation.LevelEvent<E>> drain()`

### `public synchronized java.util.List<io.casehub.agency.summarisation.LevelEvent<E>> drainIfReady(long now)`

#### Parameters

- `now` (`long`)

### `public synchronized boolean shouldEmit(long now)`

#### Parameters

- `now` (`long`)

### `public synchronized int size()`
