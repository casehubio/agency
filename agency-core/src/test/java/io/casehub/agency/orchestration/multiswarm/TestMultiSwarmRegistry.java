package io.casehub.agency.orchestration.multiswarm;

import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;
import io.casehub.api.spi.coordination.RegistryListener;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

class TestMultiSwarmRegistry implements MultiSwarmRegistry {

    private final ConcurrentHashMap<UUID, SwarmDescriptor> swarms = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<RegistryListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void register(SwarmDescriptor d) {
        swarms.put(d.swarmId(), d);
        listeners.forEach(l -> l.onRegistered(d));
    }

    @Override
    public void deregister(UUID id) {
        if (swarms.remove(id) != null) {
            listeners.forEach(l -> l.onDeregistered(id));
        }
    }

    @Override
    public void updateInterests(UUID id, Set<String> caps) {
        listeners.forEach(l -> l.onInterestsUpdated(id, caps));
    }

    @Override
    public List<SwarmDescriptor> activeSwarms(UUID caseId) {
        return swarms.values().stream().filter(d -> d.caseId().equals(caseId)).toList();
    }

    @Override
    public List<SwarmDescriptor> allActive() {
        return List.copyOf(swarms.values());
    }

    @Override
    public List<SwarmDescriptor> children(UUID parentId) {
        return swarms.values().stream().filter(d -> parentId.equals(d.parentSwarmId())).toList();
    }

    @Override
    public @Nullable SwarmDescriptor get(UUID id) {
        return swarms.get(id);
    }

    @Override
    public void addListener(RegistryListener l) {
        listeners.add(l);
    }
}
