# io.casehub.agency.perception.observation.ObserverState

**Package:** `io.casehub.agency.perception.observation`

**Kind:** `class`

## Fields

### `accumulators` (`java.util.concurrent.ConcurrentHashMap<K,io.casehub.agency.perception.observation.ObservationAccumulator<E>>`)

### `rememberedCache` (`java.util.LinkedHashMap<K,io.casehub.agency.perception.observation.RememberedPartition>`)

### `renderer` (`io.casehub.agency.perception.observation.ObservationRenderer<E>`)

## Constructors

### `public ObserverState(K initialPartition, io.casehub.agency.perception.observation.ObservationRenderer<E> renderer)`

#### Parameters

- `initialPartition` (`K`)
- `renderer` (`io.casehub.agency.perception.observation.ObservationRenderer<E>`)

## Methods

### `public io.casehub.agency.perception.observation.ObservationAccumulator<E> accumulatorFor(K partition)`

#### Parameters

- `partition` (`K`)

### `public java.util.Map<K,io.casehub.agency.perception.observation.ObservationAccumulator<E>> accumulators()`

### `public java.util.LinkedHashMap<K,io.casehub.agency.perception.observation.RememberedPartition> rememberedCache()`
