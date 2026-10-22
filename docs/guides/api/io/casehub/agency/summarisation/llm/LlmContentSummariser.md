# io.casehub.agency.perception.llm.LlmContentSummariser

**Package:** `io.casehub.agency.perception.llm`

**Kind:** `class`

## Fields

### `APPEND_PROMPT` (`java.lang.String`)

### `EDIT_PROMPT` (`java.lang.String`)

### `agentProvider` (`AgentProvider`)

### `mode` (`io.casehub.agency.summarisation.SummaryMode`)

### `preamble` (`java.lang.String`)

### `renderer` (`java.util.function.Function<T,java.lang.String>`)

## Constructors

### `public LlmContentSummariser(AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> renderer, io.casehub.agency.summarisation.SummaryMode mode)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `renderer` (`java.util.function.Function<T,java.lang.String>`)
- `mode` (`io.casehub.agency.summarisation.SummaryMode`)

### `public LlmContentSummariser(AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> renderer, io.casehub.agency.summarisation.SummaryMode mode, java.lang.String preamble)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `renderer` (`java.util.function.Function<T,java.lang.String>`)
- `mode` (`io.casehub.agency.summarisation.SummaryMode`)
- `preamble` (`java.lang.String`)

## Methods

### `private java.lang.String buildPrompt(java.util.List<T> items, SummaryResult previous)`

#### Parameters

- `items` (`java.util.List<T>`)
- `previous` (`SummaryResult`)

### `public java.util.concurrent.CompletionStage<SummaryResult> summarise(java.util.List<T> items, SummaryResult previous)`

#### Parameters

- `items` (`java.util.List<T>`)
- `previous` (`SummaryResult`)
