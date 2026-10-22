# io.casehub.agency.perception.observation.affordance.AffordanceRenderer

**Package:** `io.casehub.agency.perception.observation.affordance`

**Kind:** `class`

## Fields

### `DEFAULT_HEADER_FORMATTER` (`java.util.function.Function<java.lang.String,java.lang.String>`)

### `headerFormatter` (`java.util.function.Function<java.lang.String,java.lang.String>`)

## Constructors

### `public AffordanceRenderer()`

### `public AffordanceRenderer(java.util.function.Function<java.lang.String,java.lang.String> headerFormatter)`

#### Parameters

- `headerFormatter` (`java.util.function.Function<java.lang.String,java.lang.String>`)

## Methods

### `public java.lang.String renderActionVocabulary(java.lang.String header, java.util.List<io.casehub.agency.perception.observation.affordance.ActionDescriptor> actions)`

#### Parameters

- `header` (`java.lang.String`)
- `actions` (`java.util.List<io.casehub.agency.perception.observation.affordance.ActionDescriptor>`)

### `private java.lang.String renderAffordance(io.casehub.agency.perception.observation.affordance.Affordance affordance)`

#### Parameters

- `affordance` (`io.casehub.agency.perception.observation.affordance.Affordance`)

### `public java.lang.String renderEntities(java.util.List<io.casehub.agency.perception.observation.affordance.ObservableEntity> entities)`

#### Parameters

- `entities` (`java.util.List<io.casehub.agency.perception.observation.affordance.ObservableEntity>`)

### `public java.lang.String renderEntities(java.util.List<io.casehub.agency.perception.observation.affordance.ObservableEntity> entities, java.lang.String emptyMessage)`

#### Parameters

- `entities` (`java.util.List<io.casehub.agency.perception.observation.affordance.ObservableEntity>`)
- `emptyMessage` (`java.lang.String`)

### `private java.lang.String renderEntity(io.casehub.agency.perception.observation.affordance.ObservableEntity entity)`

#### Parameters

- `entity` (`io.casehub.agency.perception.observation.affordance.ObservableEntity`)

### `public java.lang.String renderObservation(java.util.List<io.casehub.agency.perception.observation.affordance.ObservationSection> sections)`

#### Parameters

- `sections` (`java.util.List<io.casehub.agency.perception.observation.affordance.ObservationSection>`)

### `private java.lang.String renderSection(io.casehub.agency.perception.observation.affordance.ObservationSection section)`

#### Parameters

- `section` (`io.casehub.agency.perception.observation.affordance.ObservationSection`)

### `public io.casehub.agency.perception.observation.affordance.AffordanceRenderer withHeaderFormatter(java.util.function.Function<java.lang.String,java.lang.String> headerFormatter)`

#### Parameters

- `headerFormatter` (`java.util.function.Function<java.lang.String,java.lang.String>`)
