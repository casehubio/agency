# io.casehub.agency.channel.ChannelAgentHandler

**Package:** `io.casehub.agency.channel`

**Kind:** `interface`

SPI for channel-reactive agent handlers.
Handlers must have non-overlapping handles() predicates — first-match routing.

## Methods

### `public abstract MessageDispatch buildResponse(java.util.UUID channelId, java.lang.String senderId, java.lang.String llmOutput, io.casehub.agency.channel.ChannelAgentRequest trigger)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `senderId` (`java.lang.String`)
- `llmOutput` (`java.lang.String`)
- `trigger` (`io.casehub.agency.channel.ChannelAgentRequest`)

### `public abstract boolean handles(io.casehub.agency.channel.ChannelAgentRequest request)`

#### Parameters

- `request` (`io.casehub.agency.channel.ChannelAgentRequest`)

### `public abstract io.casehub.agency.channel.AgentTask prepareTask(io.casehub.agency.channel.ChannelAgentRequest request)`

#### Parameters

- `request` (`io.casehub.agency.channel.ChannelAgentRequest`)
