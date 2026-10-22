# io.casehub.agency.orchestration.decomposition.LlmDecomposition

**Package:** `io.casehub.agency.orchestration.decomposition`

**Kind:** `class`

## Fields

### `FLAT_SYSTEM_PROMPT` (`java.lang.String`)

### `LOG` (`java.lang.System.Logger`)

### `MAPPER` (`ObjectMapper`)

### `RECURSIVE_SYSTEM_PROMPT` (`java.lang.String`)

### `agentProvider` (`AgentProvider`)

### `maxDepth` (`int`)

### `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)

### `systemPromptCustomiser` (`io.casehub.agency.optimisation.prompt.SystemPromptCustomiser`)

### `variantSelector` (`io.casehub.agency.optimisation.prompt.VariantSelector`)

### `variantStore` (`io.casehub.agency.optimisation.prompt.PromptVariantStore`)

## Constructors

### `public LlmDecomposition(AgentProvider agentProvider)`

#### Parameters

- `agentProvider` (`AgentProvider`)

### `public LlmDecomposition(AgentProvider agentProvider, int maxDepth)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `maxDepth` (`int`)

### `public LlmDecomposition(AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> stateRenderer)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)

### `public LlmDecomposition(AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> stateRenderer, int maxDepth)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)
- `maxDepth` (`int`)

### `public LlmDecomposition(AgentProvider agentProvider, java.util.function.Function<T,java.lang.String> stateRenderer, int maxDepth, io.casehub.agency.optimisation.prompt.SystemPromptCustomiser systemPromptCustomiser, io.casehub.agency.optimisation.prompt.VariantSelector variantSelector, io.casehub.agency.optimisation.prompt.PromptVariantStore variantStore)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `stateRenderer` (`java.util.function.Function<T,java.lang.String>`)
- `maxDepth` (`int`)
- `systemPromptCustomiser` (`io.casehub.agency.optimisation.prompt.SystemPromptCustomiser`)
- `variantSelector` (`io.casehub.agency.optimisation.prompt.VariantSelector`)
- `variantStore` (`io.casehub.agency.optimisation.prompt.PromptVariantStore`)

## Methods

### `private void appendFewShotExamples(java.lang.StringBuilder sb)`

#### Parameters

- `sb` (`java.lang.StringBuilder`)

### `private java.lang.String buildUserPrompt(TaskNode.CompoundTask<T> goal, io.casehub.agency.orchestration.decomposition.AgenticDecompositionContext<T> context)`

#### Parameters

- `goal` (`TaskNode.CompoundTask<T>`)
- `context` (`io.casehub.agency.orchestration.decomposition.AgenticDecompositionContext<T>`)

### `private static java.util.List<java.lang.String> collectEntryNames(JsonNode root)`

#### Parameters

- `root` (`JsonNode`)

### `public Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> compound, DecompositionContext<T> context)`

#### Parameters

- `compound` (`TaskNode<T>`)
- `context` (`DecompositionContext<T>`)

### `private static io.casehub.agency.core.AgentRef resolveAgent(java.lang.String name, java.util.List<io.casehub.agency.core.RoutingCandidate> agents)`

#### Parameters

- `name` (`java.lang.String`)
- `agents` (`java.util.List<io.casehub.agency.core.RoutingCandidate>`)

### `private TaskNode.@Nullable LeafTask<T> resolveAgentEntry(JsonNode node, java.lang.String taskName, io.casehub.agency.orchestration.decomposition.AgenticDecompositionContext<T> ctx)`

#### Parameters

- `node` (`JsonNode`)
- `taskName` (`java.lang.String`)
- `ctx` (`io.casehub.agency.orchestration.decomposition.AgenticDecompositionContext<T>`)

### `private DagPlan<TaskNode.LeafTask<T>> resolveEntries(java.lang.String text, java.lang.String taskName, io.casehub.agency.orchestration.decomposition.AgenticDecompositionContext<T> ctx)`

#### Parameters

- `text` (`java.lang.String`)
- `taskName` (`java.lang.String`)
- `ctx` (`io.casehub.agency.orchestration.decomposition.AgenticDecompositionContext<T>`)

### `private DagPlan<TaskNode.LeafTask<T>> resolveSubtaskEntry(JsonNode node, java.lang.String taskName, io.casehub.agency.orchestration.decomposition.AgenticDecompositionContext<T> ctx, java.util.List<java.lang.String> siblingNames)`

#### Parameters

- `node` (`JsonNode`)
- `taskName` (`java.lang.String`)
- `ctx` (`io.casehub.agency.orchestration.decomposition.AgenticDecompositionContext<T>`)
- `siblingNames` (`java.util.List<java.lang.String>`)

### `private java.lang.String selectSystemPrompt(int currentDepth)`

#### Parameters

- `currentDepth` (`int`)

### `private static java.lang.String stripCodeFence(java.lang.String text)`

#### Parameters

- `text` (`java.lang.String`)
