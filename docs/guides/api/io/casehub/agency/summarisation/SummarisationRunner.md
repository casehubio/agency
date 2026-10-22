# io.casehub.agency.summarisation.SummarisationRunner

**Package:** `io.casehub.agency.summarisation`

**Kind:** `class`

## Fields

### `LOG` (`java.lang.System.Logger`)

### `accumulator` (`io.casehub.agency.summarisation.EventAccumulator<IN>`)

### `compactor` (`io.casehub.agency.summarisation.Compactor<IN>`)

### `onFailure` (`java.util.function.Consumer<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>>`)

### `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<OUT>`)

### `outputLevel` (`io.casehub.agency.summarisation.EventLevel`)

### `summariser` (`io.casehub.agency.summarisation.Summariser<IN,OUT>`)

## Constructors

### `public SummarisationRunner(io.casehub.agency.summarisation.WindowPolicy policy, io.casehub.agency.summarisation.Compactor<IN> compactor, io.casehub.agency.summarisation.Summariser<IN,OUT> summariser, io.casehub.agency.summarisation.EventStreamBus<OUT> outputBus, io.casehub.agency.summarisation.EventLevel outputLevel)`

#### Parameters

- `policy` (`io.casehub.agency.summarisation.WindowPolicy`)
- `compactor` (`io.casehub.agency.summarisation.Compactor<IN>`)
- `summariser` (`io.casehub.agency.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.agency.summarisation.EventLevel`)

### `public SummarisationRunner(io.casehub.agency.summarisation.WindowPolicy policy, io.casehub.agency.summarisation.Compactor<IN> compactor, io.casehub.agency.summarisation.Summariser<IN,OUT> summariser, io.casehub.agency.summarisation.EventStreamBus<OUT> outputBus, io.casehub.agency.summarisation.EventLevel outputLevel, java.util.function.Consumer<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>> onFailure)`

#### Parameters

- `policy` (`io.casehub.agency.summarisation.WindowPolicy`)
- `compactor` (`io.casehub.agency.summarisation.Compactor<IN>`)
- `summariser` (`io.casehub.agency.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.agency.summarisation.EventLevel`)
- `onFailure` (`java.util.function.Consumer<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>>`)

### `public SummarisationRunner(io.casehub.agency.summarisation.WindowPolicy policy, io.casehub.agency.summarisation.Summariser<IN,OUT> summariser, io.casehub.agency.summarisation.EventStreamBus<OUT> outputBus, io.casehub.agency.summarisation.EventLevel outputLevel)`

#### Parameters

- `policy` (`io.casehub.agency.summarisation.WindowPolicy`)
- `summariser` (`io.casehub.agency.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.agency.summarisation.EventLevel`)

### `public SummarisationRunner(io.casehub.agency.summarisation.WindowPolicy policy, io.casehub.agency.summarisation.Summariser<IN,OUT> summariser, io.casehub.agency.summarisation.EventStreamBus<OUT> outputBus, io.casehub.agency.summarisation.EventLevel outputLevel, java.util.function.Consumer<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>> onFailure)`

#### Parameters

- `policy` (`io.casehub.agency.summarisation.WindowPolicy`)
- `summariser` (`io.casehub.agency.summarisation.Summariser<IN,OUT>`)
- `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<OUT>`)
- `outputLevel` (`io.casehub.agency.summarisation.EventLevel`)
- `onFailure` (`java.util.function.Consumer<java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>>`)

## Methods

### `public void clear()`

### `public void collect(io.casehub.agency.summarisation.LevelEvent<IN> event)`

#### Parameters

- `event` (`io.casehub.agency.summarisation.LevelEvent<IN>`)

### `public synchronized java.util.concurrent.CompletionStage<java.lang.Void> flush()`

Unconditional drain — bypasses WindowPolicy. Use at shutdown to
capture all remaining buffered events regardless of count or age.

### `public int size()`

### `public synchronized java.util.concurrent.CompletionStage<java.lang.Void> tick(long now)`

Drains ready events, applies compaction, and submits to the summariser.
Synchronized — concurrent tick() calls are serialized. The hot path
(no events ready) acquires and releases the lock without blocking.

#### Parameters

- `now` (`long`)
