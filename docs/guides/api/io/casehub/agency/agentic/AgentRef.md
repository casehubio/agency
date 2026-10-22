# io.casehub.agency.agentic.AgentRef

**Package:** `io.casehub.agency.agentic`

**Kind:** `interface`

## Methods

### `public static io.casehub.agency.agentic.AgentRef.ChannelAgent channel(java.util.UUID channelId, io.casehub.agency.channel.ChannelAgentHandler handler)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `handler` (`io.casehub.agency.channel.ChannelAgentHandler`)

### `public static io.casehub.agency.agentic.AgentRef.ComposedAgent composed(io.casehub.agency.agentic.model.ExecutionModel<?> model)`

#### Parameters

- `model` (`io.casehub.agency.agentic.model.ExecutionModel<?>`)

### `public static io.casehub.agency.agentic.AgentRef.ExternalAgent external(java.lang.String label, java.util.function.Function<T,java.util.concurrent.CompletionStage<io.casehub.agency.agentic.AgentResult>> fn)`

#### Parameters

- `label` (`java.lang.String`)
- `fn` (`java.util.function.Function<T,java.util.concurrent.CompletionStage<io.casehub.agency.agentic.AgentResult>>`)

### `public static io.casehub.agency.agentic.AgentRef.ExternalAgent external(java.util.function.Function<T,java.util.concurrent.CompletionStage<io.casehub.agency.agentic.AgentResult>> fn)`

#### Parameters

- `fn` (`java.util.function.Function<T,java.util.concurrent.CompletionStage<io.casehub.agency.agentic.AgentResult>>`)

### `public static io.casehub.agency.agentic.AgentRef.HumanAgent human(WorkItemCreateRequest template)`

#### Parameters

- `template` (`WorkItemCreateRequest`)

### `public static io.casehub.agency.agentic.AgentRef.WorkerAgent worker(Worker worker)`

#### Parameters

- `worker` (`Worker`)
