# io.casehub.agency.deliberation.conversation.ConvergenceAnalyser

**Package:** `io.casehub.agency.deliberation.conversation`

**Kind:** `class`

## Constructors

### `private ConvergenceAnalyser()`

## Methods

### `public static io.casehub.agency.deliberation.conversation.ConvergenceSignal analyse(io.casehub.agency.deliberation.conversation.ConversationState state, io.casehub.agency.deliberation.conversation.CommonGroundState commonGround, io.casehub.agency.deliberation.conversation.ConvergencePolicy policy, int recentWindow)`

#### Parameters

- `state` (`io.casehub.agency.deliberation.conversation.ConversationState`)
- `commonGround` (`io.casehub.agency.deliberation.conversation.CommonGroundState`)
- `policy` (`io.casehub.agency.deliberation.conversation.ConvergencePolicy`)
- `recentWindow` (`int`)

### `static io.casehub.agency.deliberation.conversation.ConvergenceContext buildContext(io.casehub.agency.deliberation.conversation.ConversationState state, io.casehub.agency.deliberation.conversation.CommonGroundState commonGround, int recentWindow)`

#### Parameters

- `state` (`io.casehub.agency.deliberation.conversation.ConversationState`)
- `commonGround` (`io.casehub.agency.deliberation.conversation.CommonGroundState`)
- `recentWindow` (`int`)

### `private static double computeLengthTrend(java.util.List<io.casehub.agency.deliberation.conversation.ThreadEntry> all, java.util.List<io.casehub.agency.deliberation.conversation.ThreadEntry> recent)`

#### Parameters

- `all` (`java.util.List<io.casehub.agency.deliberation.conversation.ThreadEntry>`)
- `recent` (`java.util.List<io.casehub.agency.deliberation.conversation.ThreadEntry>`)

### `private static double computeSimilarity(java.util.List<io.casehub.agency.deliberation.conversation.ThreadEntry> recent)`

#### Parameters

- `recent` (`java.util.List<io.casehub.agency.deliberation.conversation.ThreadEntry>`)

### `private static java.util.List<io.casehub.agency.deliberation.conversation.ThreadEntry> flattenAndSort(io.casehub.agency.deliberation.conversation.ConversationState state)`

#### Parameters

- `state` (`io.casehub.agency.deliberation.conversation.ConversationState`)

### `static double jaccardSimilarity(java.util.Set<java.lang.String> a, java.util.Set<java.lang.String> b)`

#### Parameters

- `a` (`java.util.Set<java.lang.String>`)
- `b` (`java.util.Set<java.lang.String>`)

### `private static int lastNewPointRound(io.casehub.agency.deliberation.conversation.ConversationState state)`

#### Parameters

- `state` (`io.casehub.agency.deliberation.conversation.ConversationState`)

### `private static int lastStatusChangeRound(io.casehub.agency.deliberation.conversation.ConversationState state, io.casehub.agency.deliberation.conversation.CommonGroundState commonGround)`

#### Parameters

- `state` (`io.casehub.agency.deliberation.conversation.ConversationState`)
- `commonGround` (`io.casehub.agency.deliberation.conversation.CommonGroundState`)

### `static java.util.Set<java.lang.String> tokenize(java.lang.String text)`

#### Parameters

- `text` (`java.lang.String`)
