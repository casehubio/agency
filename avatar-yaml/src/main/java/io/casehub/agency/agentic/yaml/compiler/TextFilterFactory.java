package io.casehub.agency.core.yaml.compiler;

import io.casehub.connectors.voice.spi.TextFilter;

import java.util.Map;

@FunctionalInterface
public interface TextFilterFactory {
    TextFilter create(Map<String, Object> config);
}
