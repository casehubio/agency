package io.casehub.agency.summarisation;

import org.jspecify.annotations.Nullable;

import java.util.List;

@FunctionalInterface
public interface OutputProcessor<OUT, S> {
    List<OUT> process(List<OUT> outputs, @Nullable S currentState);
}
