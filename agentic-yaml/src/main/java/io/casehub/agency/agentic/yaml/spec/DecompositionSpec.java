package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = DecompositionSpec.Identity.class, name = "identity"),
        @Type(value = DecompositionSpec.Static.class, name = "static"),
        @Type(value = DecompositionSpec.Llm.class, name = "llm"),
        @Type(value = DecompositionSpec.Hybrid.class, name = "hybrid"),
        @Type(value = DecompositionSpec.Heuristic.class, name = "heuristic"),
        @Type(value = DecompositionSpec.Goap.class, name = "goap"),
        @Type(value = DecompositionSpec.CapabilityDependency.class, name = "capability-dependency"),
        @Type(value = DecompositionSpec.ForwardReasoning.class, name = "forward-reasoning")
})
public sealed interface DecompositionSpec {

    record Identity() implements DecompositionSpec {}

    record Static(List<MethodSpec> methods) implements DecompositionSpec {}

    record Llm(@Nullable Integer maxDepth) implements DecompositionSpec {}

    record Hybrid(@Nullable Integer maxDepth) implements DecompositionSpec {}

    record Heuristic(@Nullable String name) implements DecompositionSpec {}

    record Goap() implements DecompositionSpec {}

    record CapabilityDependency() implements DecompositionSpec {}

    record ForwardReasoning() implements DecompositionSpec {}
}
