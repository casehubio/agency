package io.casehub.agency.summarisation.yaml;

import io.casehub.agency.summarisation.Summariser;

import java.util.Map;

@FunctionalInterface
public interface SummariserFactory<E> {
    Summariser<E, ?> create(Map<String, Object> config);
}
