# io.casehub.agency.perception.observation.TieredObservationRenderer

**Package:** `io.casehub.agency.perception.observation`

**Kind:** `class`

## Fields

### `eventRenderer` (`java.util.function.Function<E,java.lang.String>`)

### `groupKeyExtractor` (`java.util.function.Function<E,java.lang.String>`)

### `groupedThreshold` (`int`)

### `headerFormatter` (`java.util.function.Function<io.casehub.agency.perception.observation.ObservationContext,java.lang.String>`)

### `summariser` (`io.casehub.agency.summarisation.Summariser<E,java.lang.String>`)

### `verbatimThreshold` (`int`)

## Constructors

### `public TieredObservationRenderer(java.util.function.Function<E,java.lang.String> eventRenderer, java.util.function.Function<E,java.lang.String> groupKeyExtractor, int verbatimThreshold)`

#### Parameters

- `eventRenderer` (`java.util.function.Function<E,java.lang.String>`)
- `groupKeyExtractor` (`java.util.function.Function<E,java.lang.String>`)
- `verbatimThreshold` (`int`)

### `public TieredObservationRenderer(java.util.function.Function<E,java.lang.String> eventRenderer, java.util.function.Function<E,java.lang.String> groupKeyExtractor, int verbatimThreshold, int groupedThreshold, io.casehub.agency.summarisation.Summariser<E,java.lang.String> summariser)`

#### Parameters

- `eventRenderer` (`java.util.function.Function<E,java.lang.String>`)
- `groupKeyExtractor` (`java.util.function.Function<E,java.lang.String>`)
- `verbatimThreshold` (`int`)
- `groupedThreshold` (`int`)
- `summariser` (`io.casehub.agency.summarisation.Summariser<E,java.lang.String>`)

### `private TieredObservationRenderer(java.util.function.Function<E,java.lang.String> eventRenderer, java.util.function.Function<E,java.lang.String> groupKeyExtractor, int verbatimThreshold, int groupedThreshold, io.casehub.agency.summarisation.Summariser<E,java.lang.String> summariser, java.util.function.Function<io.casehub.agency.perception.observation.ObservationContext,java.lang.String> headerFormatter)`

#### Parameters

- `eventRenderer` (`java.util.function.Function<E,java.lang.String>`)
- `groupKeyExtractor` (`java.util.function.Function<E,java.lang.String>`)
- `verbatimThreshold` (`int`)
- `groupedThreshold` (`int`)
- `summariser` (`io.casehub.agency.summarisation.Summariser<E,java.lang.String>`)
- `headerFormatter` (`java.util.function.Function<io.casehub.agency.perception.observation.ObservationContext,java.lang.String>`)

## Methods

### `private static java.lang.String defaultHeader(io.casehub.agency.perception.observation.ObservationContext context)`

#### Parameters

- `context` (`io.casehub.agency.perception.observation.ObservationContext`)

### `static java.lang.String formatAgo(long millis)`

#### Parameters

- `millis` (`long`)

### `static java.lang.String formatDuration(long millis)`

#### Parameters

- `millis` (`long`)

### `public java.util.concurrent.CompletionStage<io.casehub.agency.perception.observation.ObservationResult> render(java.util.List<io.casehub.agency.summarisation.LevelEvent<E>> events, io.casehub.agency.perception.observation.ObservationContext context)`

#### Parameters

- `events` (`java.util.List<io.casehub.agency.summarisation.LevelEvent<E>>`)
- `context` (`io.casehub.agency.perception.observation.ObservationContext`)

### `private io.casehub.agency.perception.observation.ObservationResult renderGrouped(java.util.List<io.casehub.agency.summarisation.LevelEvent<E>> events, io.casehub.agency.perception.observation.ObservationContext context, java.lang.String header)`

#### Parameters

- `events` (`java.util.List<io.casehub.agency.summarisation.LevelEvent<E>>`)
- `context` (`io.casehub.agency.perception.observation.ObservationContext`)
- `header` (`java.lang.String`)

### `private java.util.concurrent.CompletionStage<io.casehub.agency.perception.observation.ObservationResult> renderSummarised(java.util.List<io.casehub.agency.summarisation.LevelEvent<E>> events, io.casehub.agency.perception.observation.ObservationContext context, java.lang.String header)`

#### Parameters

- `events` (`java.util.List<io.casehub.agency.summarisation.LevelEvent<E>>`)
- `context` (`io.casehub.agency.perception.observation.ObservationContext`)
- `header` (`java.lang.String`)

### `private io.casehub.agency.perception.observation.ObservationResult renderVerbatim(java.util.List<io.casehub.agency.summarisation.LevelEvent<E>> events, io.casehub.agency.perception.observation.ObservationContext context, java.lang.String header)`

#### Parameters

- `events` (`java.util.List<io.casehub.agency.summarisation.LevelEvent<E>>`)
- `context` (`io.casehub.agency.perception.observation.ObservationContext`)
- `header` (`java.lang.String`)

### `public io.casehub.agency.perception.observation.TieredObservationRenderer<E> withHeaderFormatter(java.util.function.Function<io.casehub.agency.perception.observation.ObservationContext,java.lang.String> headerFormatter)`

#### Parameters

- `headerFormatter` (`java.util.function.Function<io.casehub.agency.perception.observation.ObservationContext,java.lang.String>`)
