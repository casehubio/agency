# io.casehub.agency.orchestration.decomposition.OutputContract

**Package:** `io.casehub.agency.orchestration.decomposition`

**Kind:** `interface`

Declares the expected shape of a task's output, enabling validation gates between tasks.
Failed validation triggers retry at task granularity rather than pipeline restart.

<p>Composable via `.and(OutputContract)`.

## Methods

### `public default io.casehub.agency.orchestration.decomposition.OutputContract and(io.casehub.agency.orchestration.decomposition.OutputContract other)`

#### Parameters

- `other` (`io.casehub.agency.orchestration.decomposition.OutputContract`)

### `public static io.casehub.agency.orchestration.decomposition.OutputContract nonNull()`

### `public static io.casehub.agency.orchestration.decomposition.OutputContract of(java.util.function.Predicate<java.lang.Object> test)`

#### Parameters

- `test` (`java.util.function.Predicate<java.lang.Object>`)

### `public static io.casehub.agency.orchestration.decomposition.OutputContract type(java.lang.Class<?> expected)`

#### Parameters

- `expected` (`java.lang.Class<?>`)

### `public abstract boolean validate(java.lang.Object output)`

#### Parameters

- `output` (`java.lang.Object`)
