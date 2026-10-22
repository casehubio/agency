# io.casehub.agency.communication.dispatch.ChannelAgentHandler

**Package:** `io.casehub.agency.communication.dispatch`

**Kind:** `interface`

SPI for channel-reactive agent handlers.
Handlers must have non-overlapping handles() predicates — first-match routing.

## Methods

### `public abstract MessageDispatch buildResponse(java.util.UUID channelId, java.lang.String senderId, java.lang.String llmOutput, io.casehub.agency.communication.dispatch.ChannelAgentRequest trigger)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `senderId` (`java.lang.String`)
- `llmOutput` (`java.lang.String`)
- `trigger` (`io.casehub.agency.communication.dispatch.ChannelAgentRequest`)

### `public abstract boolean handles(io.casehub.agency.communication.dispatch.ChannelAgentRequest request)`

#### Parameters

- `request` (`io.casehub.agency.communication.dispatch.ChannelAgentRequest`)

### `public abstract io.casehub.agency.communication.dispatch.AgentTask prepareTask(io.casehub.agency.communication.dispatch.ChannelAgentRequest request)`

#### Parameters

- `request` (`io.casehub.agency.communication.dispatch.ChannelAgentRequest`)
