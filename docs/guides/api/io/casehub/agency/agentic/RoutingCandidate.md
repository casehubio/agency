# io.casehub.agency.agentic.RoutingCandidate

**Package:** `io.casehub.agency.agentic`

**Kind:** `record`

A candidate for agent routing — pairs a dispatch reference with an optional identity profile.

## Fields

### `descriptor` (`AgentDescriptor`)

### `ref` (`io.casehub.agency.agentic.AgentRef`)

## Record Components

### `descriptor` (`AgentDescriptor`)

the agent's identity profile for routing metadata.
                  Nullable — pattern builders that accept bare `AgentRef`
                  create candidates without descriptors. Routing strategies
                  must handle null descriptors gracefully.

### `ref` (`io.casehub.agency.agentic.AgentRef`)

the agent dispatch reference — never null

## Constructors

### `public RoutingCandidate(io.casehub.agency.agentic.AgentRef ref, AgentDescriptor descriptor)`

#### Parameters

- `ref` (`io.casehub.agency.agentic.AgentRef`)
- `descriptor` (`AgentDescriptor`)

## Methods

### `public AgentDescriptor descriptor()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.agency.agentic.AgentRef ref()`

### `public final java.lang.String toString()`
