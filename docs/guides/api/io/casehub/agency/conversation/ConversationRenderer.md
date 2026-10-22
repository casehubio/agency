# io.casehub.agency.conversation.ConversationRenderer

**Package:** `io.casehub.agency.conversation`

**Kind:** `class`

## Fields

### `DEFAULT_EMOJI` (`java.lang.String`)

### `config` (`io.casehub.agency.conversation.ConversationRendererConfig`)

### `progressRenderer` (`io.casehub.agency.conversation.ProgressRenderer`)

## Constructors

### `public ConversationRenderer(io.casehub.agency.conversation.ConversationRendererConfig config)`

#### Parameters

- `config` (`io.casehub.agency.conversation.ConversationRendererConfig`)

### `public ConversationRenderer(io.casehub.agency.conversation.ConversationRendererConfig config, io.casehub.agency.conversation.ProgressRenderer progressRenderer)`

#### Parameters

- `config` (`io.casehub.agency.conversation.ConversationRendererConfig`)
- `progressRenderer` (`io.casehub.agency.conversation.ProgressRenderer`)

## Methods

### `public java.lang.String render(io.casehub.agency.conversation.ConversationState state)`

#### Parameters

- `state` (`io.casehub.agency.conversation.ConversationState`)

### `public java.lang.String render(io.casehub.agency.conversation.ConversationState state, io.casehub.agency.conversation.RenderContext ctx)`

#### Parameters

- `state` (`io.casehub.agency.conversation.ConversationState`)
- `ctx` (`io.casehub.agency.conversation.RenderContext`)

### `private void renderByTopic(java.lang.StringBuilder sb, io.casehub.agency.conversation.ConversationState state, io.casehub.agency.conversation.RenderContext ctx)`

#### Parameters

- `sb` (`java.lang.StringBuilder`)
- `state` (`io.casehub.agency.conversation.ConversationState`)
- `ctx` (`io.casehub.agency.conversation.RenderContext`)

### `private void renderEpistemicBadge(java.lang.StringBuilder sb, io.casehub.agency.conversation.ConversationPoint point, io.casehub.agency.conversation.CommonGroundState cg)`

#### Parameters

- `sb` (`java.lang.StringBuilder`)
- `point` (`io.casehub.agency.conversation.ConversationPoint`)
- `cg` (`io.casehub.agency.conversation.CommonGroundState`)

### `private java.lang.String renderFinding(io.casehub.agency.conversation.SubTaskFinding f)`

#### Parameters

- `f` (`io.casehub.agency.conversation.SubTaskFinding`)

### `private void renderFlat(java.lang.StringBuilder sb, io.casehub.agency.conversation.ConversationState state, io.casehub.agency.conversation.RenderContext ctx)`

#### Parameters

- `sb` (`java.lang.StringBuilder`)
- `state` (`io.casehub.agency.conversation.ConversationState`)
- `ctx` (`io.casehub.agency.conversation.RenderContext`)

### `private java.lang.String renderObligationChain(java.util.List<io.casehub.agency.conversation.ConversationPoint> points)`

#### Parameters

- `points` (`java.util.List<io.casehub.agency.conversation.ConversationPoint>`)

### `private void renderPoints(java.lang.StringBuilder sb, java.util.List<io.casehub.agency.conversation.ConversationPoint> points, io.casehub.agency.conversation.ConversationState state, boolean strikethrough, io.casehub.agency.conversation.RenderContext ctx)`

#### Parameters

- `sb` (`java.lang.StringBuilder`)
- `points` (`java.util.List<io.casehub.agency.conversation.ConversationPoint>`)
- `state` (`io.casehub.agency.conversation.ConversationState`)
- `strikethrough` (`boolean`)
- `ctx` (`io.casehub.agency.conversation.RenderContext`)
