package io.casehub.agency.memory;

import io.casehub.neocortex.memory.ReflectionEntry;

public class NoOpReflectionStore implements ReflectionStore {
    @Override
    public void store(ReflectionEntry entry) {}
}
