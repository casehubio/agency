package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = TaskNodeSpec.Primitive.class, name = "primitive"),
        @Type(value = TaskNodeSpec.Compound.class, name = "compound")
})
public sealed interface TaskNodeSpec {

    String name();

    record Primitive(String name, @Nullable String executor,
                     @Nullable String description) implements TaskNodeSpec {}

    record Compound(String name, List<TaskNodeSpec> subtasks) implements TaskNodeSpec {}
}
