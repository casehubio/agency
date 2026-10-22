package io.casehub.agency.social.memory;

import io.casehub.neocortex.memory.ReflectionEntry;

@FunctionalInterface
public interface ReflectionStore {
    void store(ReflectionEntry entry);
}
