# io.casehub.agency.summarisation.KeyedSummarisationRunner

**Package:** `io.casehub.agency.summarisation`

**Kind:** `class`

## Fields

### `LOG` (`java.lang.System.Logger`)

### `accumulator` (`io.casehub.agency.summarisation.KeyedAccumulator<K,IN>`)

### `compactor` (`io.casehub.agency.summarisation.Compactor<IN>`)

### `onFailure` (`java.util.function.Consumer<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>>`)

### `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<OUT>`)

### `outputLevel` (`io.casehub.agency.summarisation.EventLevel`)

### `summariser` (`io.casehub.agency.summarisation.Summariser<IN,OUT>`)

## Constructors

### `public KeyedSummarisationRunner(java.util.function.Function<IN,K> keyExtractor, java.util.function.Predicate<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>> completionTest, long staleTimeout, io.casehub.agency.summarisation.Compactor<IN> compactor, io.casehub.agency.summarisation.Summariser<IN,OUT> summariser, io.casehub.agency.summarisation.EventStreamBus<OUT> outputBus, io.casehub.agency.summarisation.EventLevel outputLevel)`

#### Parameters

- `keyExtractor` (`java.util.function.Function<IN,K>`)
- `completionTest` (`java.util.function.Predicate<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>>`)
- `staleTimeout` (`long`)
- `compactor` (`io.casehub.agency.summarisation.Compactor<IN>`)
- `summariser` (`io.casehub.agency.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.agency.summarisation.EventLevel`)

### `public KeyedSummarisationRunner(java.util.function.Function<IN,K> keyExtractor, java.util.function.Predicate<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>> completionTest, long staleTimeout, io.casehub.agency.summarisation.Compactor<IN> compactor, io.casehub.agency.summarisation.Summariser<IN,OUT> summariser, io.casehub.agency.summarisation.EventStreamBus<OUT> outputBus, io.casehub.agency.summarisation.EventLevel outputLevel, java.util.function.Consumer<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>> onFailure)`

#### Parameters

- `keyExtractor` (`java.util.function.Function<IN,K>`)
- `completionTest` (`java.util.function.Predicate<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>>`)
- `staleTimeout` (`long`)
- `compactor` (`io.casehub.agency.summarisation.Compactor<IN>`)
- `summariser` (`io.casehub.agency.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.agency.summarisation.EventLevel`)
- `onFailure` (`java.util.function.Consumer<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>>`)

### `public KeyedSummarisationRunner(java.util.function.Function<IN,K> keyExtractor, java.util.function.Predicate<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>> completionTest, long staleTimeout, io.casehub.agency.summarisation.Summariser<IN,OUT> summariser, io.casehub.agency.summarisation.EventStreamBus<OUT> outputBus, io.casehub.agency.summarisation.EventLevel outputLevel)`

#### Parameters

- `keyExtractor` (`java.util.function.Function<IN,K>`)
- `completionTest` (`java.util.function.Predicate<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>>`)
- `staleTimeout` (`long`)
- `summariser` (`io.casehub.agency.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.agency.summarisation.EventLevel`)

### `public KeyedSummarisationRunner(java.util.function.Function<IN,K> keyExtractor, java.util.function.Predicate<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>> completionTest, long staleTimeout, io.casehub.agency.summarisation.Summariser<IN,OUT> summariser, io.casehub.agency.summarisation.EventStreamBus<OUT> outputBus, io.casehub.agency.summarisation.EventLevel outputLevel, java.util.function.Consumer<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>> onFailure)`

#### Parameters

- `keyExtractor` (`java.util.function.Function<IN,K>`)
- `completionTest` (`java.util.function.Predicate<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>>`)
- `staleTimeout` (`long`)
- `summariser` (`io.casehub.agency.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.agency.summarisation.EventLevel`)
- `onFailure` (`java.util.function.Consumer<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>>`)

## Methods

### `public void clear()`

### `public void collect(io.casehub.agency.summarisation.LevelEvent<IN> event)`

#### Parameters

- `event` (`io.casehub.agency.summarisation.LevelEvent<IN>`)

### `public int eventCount()`

### `public synchronized java.util.concurrent.CompletionStage<java.lang.Void> flush()`

Unconditional drain — bypasses completion test and stale timeout.
Use at shutdown to capture all remaining buffered events.

### `public int groupCount()`

### `public synchronized java.util.concurrent.CompletionStage<java.lang.Void> tick(long now)`

Drains completed/stale groups, applies compaction, and submits each to the summariser.
Synchronized — concurrent tick() calls are serialized. The hot path
(no groups ready) acquires and releases the lock without blocking.

#### Parameters

- `now` (`long`)
