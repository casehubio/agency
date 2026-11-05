package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = Id.NAME, property = "pattern")
@JsonSubTypes({
        @Type(value = TopologySpec.Swarm.class, name = "swarm"),
        @Type(value = TopologySpec.Hive.class, name = "hive"),
        @Type(value = TopologySpec.Mesh.class, name = "mesh"),
        @Type(value = TopologySpec.Many.class, name = "many")
})
public sealed interface TopologySpec {

    @Nullable String authority();

    record Swarm(@Nullable String authority) implements TopologySpec {}

    record Hive(
            @Nullable String authority,
            @Nullable List<String> roles,
            @Nullable String orchestration,
            @Nullable Map<String, RolePolicySpec> policy
    ) implements TopologySpec {
        public Hive {
            if (roles != null) roles = List.copyOf(roles);
            if (policy != null) policy = Map.copyOf(policy);
        }
    }

    record Mesh(@Nullable String authority) implements TopologySpec {}

    record Many() implements TopologySpec {
        @Override public @Nullable String authority() { return null; }
    }

    record RolePolicySpec(
            @Nullable List<String> obligations,
            @Nullable List<String> permissions
    ) {
        public RolePolicySpec {
            if (obligations != null) obligations = List.copyOf(obligations);
            if (permissions != null) permissions = List.copyOf(permissions);
        }
    }
}
