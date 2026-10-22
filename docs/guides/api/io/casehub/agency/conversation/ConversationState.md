# io.casehub.agency.conversation.ConversationState

**Package:** `io.casehub.agency.conversation`

**Kind:** `record`

## Fields

### `humanFlags` (`java.util.List<io.casehub.agency.conversation.FlagEntry>`)

### `memos` (`java.util.List<io.casehub.agency.conversation.RoundMemo>`)

### `points` (`java.util.Map<java.lang.String,io.casehub.agency.conversation.ConversationPoint>`)

### `subTaskFindings` (`java.util.Map<java.lang.String,io.casehub.agency.conversation.SubTaskFinding>`)

## Record Components

### `humanFlags` (`java.util.List<io.casehub.agency.conversation.FlagEntry>`)

### `memos` (`java.util.List<io.casehub.agency.conversation.RoundMemo>`)

### `points` (`java.util.Map<java.lang.String,io.casehub.agency.conversation.ConversationPoint>`)

### `subTaskFindings` (`java.util.Map<java.lang.String,io.casehub.agency.conversation.SubTaskFinding>`)

## Constructors

### `public ConversationState(java.util.Map<java.lang.String,io.casehub.agency.conversation.ConversationPoint> points, java.util.List<io.casehub.agency.conversation.FlagEntry> humanFlags, java.util.List<io.casehub.agency.conversation.RoundMemo> memos, java.util.Map<java.lang.String,io.casehub.agency.conversation.SubTaskFinding> subTaskFindings)`

#### Parameters

- `points` (`java.util.Map<java.lang.String,io.casehub.agency.conversation.ConversationPoint>`)
- `humanFlags` (`java.util.List<io.casehub.agency.conversation.FlagEntry>`)
- `memos` (`java.util.List<io.casehub.agency.conversation.RoundMemo>`)
- `subTaskFindings` (`java.util.Map<java.lang.String,io.casehub.agency.conversation.SubTaskFinding>`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.List<io.casehub.agency.conversation.FlagEntry> humanFlags()`

### `public java.util.List<io.casehub.agency.conversation.RoundMemo> memos()`

### `public java.util.Map<java.lang.String,io.casehub.agency.conversation.ConversationPoint> points()`

### `public java.util.Map<java.lang.String,io.casehub.agency.conversation.SubTaskFinding> subTaskFindings()`

### `public final java.lang.String toString()`
