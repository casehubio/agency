## D1: Quarkus assembly — CognitionCoreFactory

**Choice:** Use CognitionCoreFactory directly. AgencyBeans injects the factory and calls `create(CognitionConfig.all())`.
**Alternatives:**
- Builder in AgencyBeans — duplicates resolution logic the factory already has
- Subclass CognitionCoreFactory — unnecessary complexity for small additions
**Rationale:** Factory already resolves all SPIs via CDI Instance<T>. Agency's @DefaultBean impls are on the classpath. Post-factory `chainSectionCustomizer` handles agency-specific wiring.
**Trade-offs:** Less control over construction order — but the builder's order is correct.
**Sources:** AgencyBeans.java:362-447, CognitionCoreFactory.java
**Exploration:** quick
**Status:** captured

## D2: Spring assembly — CognitivePipelineBuilder

**Choice:** Build through CognitivePipelineBuilder directly in AgencyAutoConfiguration. No Spring-specific factory.
**Alternatives:**
- Spring-specific factory with ObjectProvider<T> — more infrastructure for no benefit
**Rationale:** Builder is framework-agnostic plain Java. Spring config already has all beans resolved as method parameters and Optional<T>.
**Trade-offs:** Builder call is longer than factory.create() — but it's explicit about what's wired.
**Sources:** AgencyAutoConfiguration.java:314-382, CognitivePipelineBuilder.java
**Exploration:** quick
**Status:** captured

## D3: CognitionStack test — builder at each stage

**Choice:** CognitivePipelineBuilder at each Stage level. BASELINE sets mood+drives only, SIGNALS adds userModel+mentalModel, etc.
**Alternatives:**
- Keep 8-arg constructor — not viable once constructor goes package-private (different package)
**Rationale:** Builder supports partial construction (all fields nullable). Fidelity levels map directly to builder method calls.
**Trade-offs:** CognitionStack becomes slightly more verbose — but gains all the capabilities the builder wires (regulation, appraisal at higher stages).
**Sources:** CognitionStack.java (agentic-yaml test), CognitivePipelineBuilder.java
**Exploration:** quick
**Status:** captured

## D4: EmergentGoalPromptSection customizer

**Choice:** Post-factory chainSectionCustomizer. After create() returns, call core.chainSectionCustomizer() to replace the default EmergentGoalPromptSection with the richer version.
**Alternatives:**
- Builder hook for section customizer — pollutes neocortex builder API for an agency concern
**Rationale:** One call, explicit, no new abstractions, agency's responsibility.
**Trade-offs:** Agency touches the core after construction — but chainSectionCustomizer is designed for this.
**Sources:** AgencyBeans.java:396-406, CognitionCore.java:734-769
**Exploration:** quick
**Status:** captured

## D5: Neocortex constructor visibility

**Choice:** Make all three CognitionCore constructors package-private.
**Alternatives:** None — stated in the issue.
**Rationale:** Prevents hand-building. Builder (same package) retains access. External code goes through builder or factory.
**Trade-offs:** Breaking change for any external code using the constructor directly — but that's the point.
**Sources:** CognitionCore.java:154-196
**Exploration:** quick
**Status:** captured
