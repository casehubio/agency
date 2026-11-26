package io.casehub.agency.core.yaml.compiler;

import io.casehub.connectors.voice.spi.TextFilter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TextFilterRegistry {

    private final Map<String, TextFilterFactory> factories = new ConcurrentHashMap<>();

    public void register(String name, TextFilterFactory factory) {
        factories.put(name, factory);
    }

    public TextFilter create(String name, Map<String, Object> config) {
        var factory = factories.get(name);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown text filter: " + name + ". Registered: " + factories.keySet());
        }
        return factory.create(config);
    }

    public boolean hasType(String name) {
        return factories.containsKey(name);
    }
}
