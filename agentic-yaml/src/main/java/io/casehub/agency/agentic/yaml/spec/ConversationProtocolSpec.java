package io.casehub.agency.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

import java.util.Set;

public record ConversationProtocolSpec(
        @Nullable String sentinel,
        @Nullable Set<String> entryTypes) {}
