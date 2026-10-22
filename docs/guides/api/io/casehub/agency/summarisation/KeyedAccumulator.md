# io.casehub.agency.summarisation.KeyedAccumulator

**Package:** `io.casehub.agency.summarisation`

**Kind:** `class`

## Fields

### `completionTest` (`java.util.function.Predicate<java.util.List<io.casehub.agency.summarisation.LevelEvent<E>>>`)

### `groups` (`java.util.Map<K,java.util.List<io.casehub.agency.summarisation.LevelEvent<E>>>`)

### `keyExtractor` (`java.util.function.Function<E,K>`)

### `lastEventTime` (`java.util.Map<K,java.lang.Long>`)

### `staleTimeout` (`long`)

## Constructors

### `public KeyedAccumulator(java.util.function.Function<E,K> keyExtractor, java.util.function.Predicate<java.util.List<io.casehub.agency.summarisation.LevelEvent<E>>> completionTest, long staleTimeout)`

#### Parameters

- `keyExtractor` (`java.util.function.Function<E,K>`)
- `completionTest` (`java.util.function.Predicate<java.util.List<io.casehub.agency.summarisation.LevelEvent<E>>>`)
- `staleTimeout` (`long`)

## Methods

### `public synchronized void clear()`

### `public synchronized void collect(io.casehub.agency.summarisation.LevelEvent<E> event)`

#### Parameters

- `event` (`io.casehub.agency.summarisation.LevelEvent<E>`)

### `public synchronized java.util.List<java.util.List<io.casehub.agency.summarisation.LevelEvent<E>>> drain(long now)`

#### Parameters

- `now` (`long`)

### `public synchronized java.util.List<java.util.List<io.casehub.agency.summarisation.LevelEvent<E>>> drainAll()`

### `public synchronized int eventCount()`

### `public synchronized int groupCount()`
