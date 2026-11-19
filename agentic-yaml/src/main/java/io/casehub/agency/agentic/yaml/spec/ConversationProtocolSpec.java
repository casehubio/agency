package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public record ConversationProtocolSpec(
        @Nullable String sentinel,
        @JsonAlias("entry-types") @Nullable Set<String> entryTypes) {}
