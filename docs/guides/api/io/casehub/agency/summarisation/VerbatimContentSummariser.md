# io.casehub.agency.summarisation.VerbatimContentSummariser

**Package:** `io.casehub.agency.summarisation`

**Kind:** `class`

## Fields

### `renderer` (`java.util.function.Function<T,java.lang.String>`)

## Constructors

### `public VerbatimContentSummariser(java.util.function.Function<T,java.lang.String> renderer)`

#### Parameters

- `renderer` (`java.util.function.Function<T,java.lang.String>`)

## Methods

### `public java.util.concurrent.CompletionStage<SummaryResult> summarise(java.util.List<T> items, SummaryResult previous)`

#### Parameters

- `items` (`java.util.List<T>`)
- `previous` (`SummaryResult`)
