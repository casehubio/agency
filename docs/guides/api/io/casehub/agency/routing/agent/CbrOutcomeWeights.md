# io.casehub.agency.governance.routing.agent.CbrOutcomeWeights

**Package:** `io.casehub.agency.governance.routing.agent`

**Kind:** `interface`

Configurable weights for step-level routing outcomes used by `CbrAgentRoutingStrategy`
when scoring workers based on historical experiences.

<p>Domain repos override the `DefaultCbrOutcomeWeights` `@DefaultBean` with
`@ApplicationScoped` to tune how different outcomes influence worker scoring.

## Methods

### `public abstract java.util.Map<RoutingOutcome,java.lang.Double> weights()`
