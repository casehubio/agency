# io.casehub.agency.conversation.ConvergenceAnalyser

**Package:** `io.casehub.agency.conversation`

**Kind:** `class`

## Constructors

### `private ConvergenceAnalyser()`

## Methods

### `public static io.casehub.agency.conversation.ConvergenceSignal analyse(io.casehub.agency.conversation.ConversationState state, io.casehub.agency.conversation.CommonGroundState commonGround, io.casehub.agency.conversation.ConvergencePolicy policy, int recentWindow)`

#### Parameters

- `state` (`io.casehub.agency.conversation.ConversationState`)
- `commonGround` (`io.casehub.agency.conversation.CommonGroundState`)
- `policy` (`io.casehub.agency.conversation.ConvergencePolicy`)
- `recentWindow` (`int`)

### `static io.casehub.agency.conversation.ConvergenceContext buildContext(io.casehub.agency.conversation.ConversationState state, io.casehub.agency.conversation.CommonGroundState commonGround, int recentWindow)`

#### Parameters

- `state` (`io.casehub.agency.conversation.ConversationState`)
- `commonGround` (`io.casehub.agency.conversation.CommonGroundState`)
- `recentWindow` (`int`)

### `private static double computeLengthTrend(java.util.List<io.casehub.agency.conversation.ThreadEntry> all, java.util.List<io.casehub.agency.conversation.ThreadEntry> recent)`

#### Parameters

- `all` (`java.util.List<io.casehub.agency.conversation.ThreadEntry>`)
- `recent` (`java.util.List<io.casehub.agency.conversation.ThreadEntry>`)

### `private static double computeSimilarity(java.util.List<io.casehub.agency.conversation.ThreadEntry> recent)`

#### Parameters

- `recent` (`java.util.List<io.casehub.agency.conversation.ThreadEntry>`)

### `private static java.util.List<io.casehub.agency.conversation.ThreadEntry> flattenAndSort(io.casehub.agency.conversation.ConversationState state)`

#### Parameters

- `state` (`io.casehub.agency.conversation.ConversationState`)

### `static double jaccardSimilarity(java.util.Set<java.lang.String> a, java.util.Set<java.lang.String> b)`

#### Parameters

- `a` (`java.util.Set<java.lang.String>`)
- `b` (`java.util.Set<java.lang.String>`)

### `private static int lastNewPointRound(io.casehub.agency.conversation.ConversationState state)`

#### Parameters

- `state` (`io.casehub.agency.conversation.ConversationState`)

### `private static int lastStatusChangeRound(io.casehub.agency.conversation.ConversationState state, io.casehub.agency.conversation.CommonGroundState commonGround)`

#### Parameters

- `state` (`io.casehub.agency.conversation.ConversationState`)
- `commonGround` (`io.casehub.agency.conversation.CommonGroundState`)

### `static java.util.Set<java.lang.String> tokenize(java.lang.String text)`

#### Parameters

- `text` (`java.lang.String`)
