# io.casehub.agency.summarisation.observation.ObserverState

**Package:** `io.casehub.agency.summarisation.observation`

**Kind:** `class`

## Fields

### `accumulators` (`java.util.concurrent.ConcurrentHashMap<K,io.casehub.agency.summarisation.observation.ObservationAccumulator<E>>`)

### `rememberedCache` (`java.util.LinkedHashMap<K,io.casehub.agency.summarisation.observation.RememberedPartition>`)

### `renderer` (`io.casehub.agency.summarisation.observation.ObservationRenderer<E>`)

## Constructors

### `public ObserverState(K initialPartition, io.casehub.agency.summarisation.observation.ObservationRenderer<E> renderer)`

#### Parameters

- `initialPartition` (`K`)
- `renderer` (`io.casehub.agency.summarisation.observation.ObservationRenderer<E>`)

## Methods

### `public io.casehub.agency.summarisation.observation.ObservationAccumulator<E> accumulatorFor(K partition)`

#### Parameters

- `partition` (`K`)

### `public java.util.Map<K,io.casehub.agency.summarisation.observation.ObservationAccumulator<E>> accumulators()`

### `public java.util.LinkedHashMap<K,io.casehub.agency.summarisation.observation.RememberedPartition> rememberedCache()`
