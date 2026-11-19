## D1: Serialization library

**Choice:** Full Jackson — both encode and decode via ObjectMapper
**Alternatives:**
- Jackson decode only — less risk to wire format, but two serialization styles in one class
- Keep Gson, modify decodeClient() — lowest risk, but inconsistent with rest of codebase
**Rationale:** Consistency with agentic-yaml, summarisation-yaml, and all other CaseHub modules. @JsonAlias works naturally for kebab-case on both paths.
**Trade-offs:** Wire format gets charOffset in Cue subtypes (backward-compatible addition). sampleRate default handling needs explicit Jackson config.
**Sources:** speech-ws/pom.xml, MessageCodec.java, agentic-yaml @JsonAlias pattern from #520
**Exploration:** quick
**Status:** captured

## D2: Kebab-case type discriminators

**Choice:** Accept both camelCase and kebab-case type discriminators via @JsonSubTypes.Type(names = {...})
**Alternatives:**
- camelCase only — simpler, but inconsistent with field-name aliasing
**Rationale:** Polyglot client friendliness. Consistent with field-name aliasing approach.
**Trade-offs:** None — first name in array controls serialization output (stays camelCase).
**Sources:** Jackson 2.12 @JsonSubTypes.Type names attribute
**Exploration:** quick
**Status:** captured
