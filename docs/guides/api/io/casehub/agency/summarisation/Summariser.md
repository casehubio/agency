# io.casehub.agency.summarisation.Summariser

**Package:** `io.casehub.agency.summarisation`

**Kind:** `interface`

## Methods

### `public static io.casehub.agency.summarisation.Summariser<IN,OUT> ofSync(io.casehub.agency.summarisation.Summariser.SyncSummariser<IN,OUT> sync)`

#### Parameters

- `sync` (`io.casehub.agency.summarisation.Summariser.SyncSummariser<IN,OUT>`)

### `public abstract java.util.concurrent.CompletionStage<java.util.List<OUT>> summarise(java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>> batch)`

#### Parameters

- `batch` (`java.util.List<io.casehub.agency.summarisation.LevelEvent<IN>>`)
