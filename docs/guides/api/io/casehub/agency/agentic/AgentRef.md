# io.casehub.agency.core.AgentRef

**Package:** `io.casehub.agency.core`

**Kind:** `interface`

## Methods

### `public static io.casehub.agency.core.AgentRef.ChannelAgent channel(java.util.UUID channelId, io.casehub.agency.communication.dispatch.ChannelAgentHandler handler)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `handler` (`io.casehub.agency.communication.dispatch.ChannelAgentHandler`)

### `public static io.casehub.agency.core.AgentRef.ComposedAgent composed(io.casehub.agency.orchestration.execution.ExecutionModel<?> model)`

#### Parameters

- `model` (`io.casehub.agency.orchestration.execution.ExecutionModel<?>`)

### `public static io.casehub.agency.core.AgentRef.ExternalAgent external(java.lang.String label, java.util.function.Function<T,java.util.concurrent.CompletionStage<io.casehub.agency.core.AgentResult>> fn)`

#### Parameters

- `label` (`java.lang.String`)
- `fn` (`java.util.function.Function<T,java.util.concurrent.CompletionStage<io.casehub.agency.core.AgentResult>>`)

### `public static io.casehub.agency.core.AgentRef.ExternalAgent external(java.util.function.Function<T,java.util.concurrent.CompletionStage<io.casehub.agency.core.AgentResult>> fn)`

#### Parameters

- `fn` (`java.util.function.Function<T,java.util.concurrent.CompletionStage<io.casehub.agency.core.AgentResult>>`)

### `public static io.casehub.agency.core.AgentRef.HumanAgent human(WorkItemCreateRequest template)`

#### Parameters

- `template` (`WorkItemCreateRequest`)

### `public static io.casehub.agency.core.AgentRef.WorkerAgent worker(Worker worker)`

#### Parameters

- `worker` (`Worker`)
