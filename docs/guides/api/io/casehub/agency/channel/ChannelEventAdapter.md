# io.casehub.agency.communication.dispatch.ChannelEventAdapter

**Package:** `io.casehub.agency.communication.dispatch`

**Kind:** `class`

## Fields

### `LOG` (`java.util.logging.Logger`)

### `channelNames` (`java.util.Set<java.lang.String>`)

### `extractor` (`java.util.function.Function<MessageReceivedEvent,E>`)

### `level` (`io.casehub.agency.summarisation.EventLevel`)

### `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<E>`)

## Constructors

### `public ChannelEventAdapter(java.util.function.Function<MessageReceivedEvent,E> extractor, io.casehub.agency.summarisation.EventLevel level, io.casehub.agency.summarisation.EventStreamBus<E> outputBus)`

#### Parameters

- `extractor` (`java.util.function.Function<MessageReceivedEvent,E>`)
- `level` (`io.casehub.agency.summarisation.EventLevel`)
- `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<E>`)

### `public ChannelEventAdapter(java.util.function.Function<MessageReceivedEvent,E> extractor, io.casehub.agency.summarisation.EventLevel level, io.casehub.agency.summarisation.EventStreamBus<E> outputBus, java.util.Set<java.lang.String> channelNames)`

#### Parameters

- `extractor` (`java.util.function.Function<MessageReceivedEvent,E>`)
- `level` (`io.casehub.agency.summarisation.EventLevel`)
- `outputBus` (`io.casehub.agency.summarisation.EventStreamBus<E>`)
- `channelNames` (`java.util.Set<java.lang.String>`)

## Methods

### `public java.util.Set<java.lang.String> channels()`

### `public void onMessage(MessageReceivedEvent event)`

#### Parameters

- `event` (`MessageReceivedEvent`)

### `public Scope scope()`
