# io.casehub.agency.deliberation.conversation.ConversationState

**Package:** `io.casehub.agency.deliberation.conversation`

**Kind:** `record`

## Fields

### `humanFlags` (`java.util.List<io.casehub.agency.deliberation.conversation.FlagEntry>`)

### `memos` (`java.util.List<io.casehub.agency.deliberation.conversation.RoundMemo>`)

### `points` (`java.util.Map<java.lang.String,io.casehub.agency.deliberation.conversation.ConversationPoint>`)

### `subTaskFindings` (`java.util.Map<java.lang.String,io.casehub.agency.deliberation.conversation.SubTaskFinding>`)

## Record Components

### `humanFlags` (`java.util.List<io.casehub.agency.deliberation.conversation.FlagEntry>`)

### `memos` (`java.util.List<io.casehub.agency.deliberation.conversation.RoundMemo>`)

### `points` (`java.util.Map<java.lang.String,io.casehub.agency.deliberation.conversation.ConversationPoint>`)

### `subTaskFindings` (`java.util.Map<java.lang.String,io.casehub.agency.deliberation.conversation.SubTaskFinding>`)

## Constructors

### `public ConversationState(java.util.Map<java.lang.String,io.casehub.agency.deliberation.conversation.ConversationPoint> points, java.util.List<io.casehub.agency.deliberation.conversation.FlagEntry> humanFlags, java.util.List<io.casehub.agency.deliberation.conversation.RoundMemo> memos, java.util.Map<java.lang.String,io.casehub.agency.deliberation.conversation.SubTaskFinding> subTaskFindings)`

#### Parameters

- `points` (`java.util.Map<java.lang.String,io.casehub.agency.deliberation.conversation.ConversationPoint>`)
- `humanFlags` (`java.util.List<io.casehub.agency.deliberation.conversation.FlagEntry>`)
- `memos` (`java.util.List<io.casehub.agency.deliberation.conversation.RoundMemo>`)
- `subTaskFindings` (`java.util.Map<java.lang.String,io.casehub.agency.deliberation.conversation.SubTaskFinding>`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.List<io.casehub.agency.deliberation.conversation.FlagEntry> humanFlags()`

### `public java.util.List<io.casehub.agency.deliberation.conversation.RoundMemo> memos()`

### `public java.util.Map<java.lang.String,io.casehub.agency.deliberation.conversation.ConversationPoint> points()`

### `public java.util.Map<java.lang.String,io.casehub.agency.deliberation.conversation.SubTaskFinding> subTaskFindings()`

### `public final java.lang.String toString()`
