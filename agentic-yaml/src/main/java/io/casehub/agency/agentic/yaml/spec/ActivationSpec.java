package io.casehub.agency.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ActivationSpec.OnDispatch.class, name = "on-dispatch"),
        @Type(value = ActivationSpec.MaxIterationsGuard.class, name = "max-iterations")
})
public sealed interface ActivationSpec {

    record OnDispatch() implements ActivationSpec {}

    record MaxIterationsGuard(int maxIterations) implements ActivationSpec {}
}
