package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.casehub.eidos.org.api.PolicyKind;
import io.casehub.eidos.org.api.RolePolicy;
import io.casehub.eidos.org.api.RoleSpec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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
            @Nullable List<String> permissions,
            @Nullable Integer minAgents,
            @Nullable Integer maxAgents,
            @Nullable String vocabulary,
            @Nullable List<String> incompatibleWith
    ) {
        public RolePolicySpec {
            if (obligations != null) obligations = List.copyOf(obligations);
            if (permissions != null) permissions = List.copyOf(permissions);
            if (incompatibleWith != null) incompatibleWith = List.copyOf(incompatibleWith);
        }

        public RolePolicySpec(@Nullable List<String> obligations, @Nullable List<String> permissions) {
            this(obligations, permissions, null, null, null, null);
        }

        public RolePolicySpec(@Nullable List<String> obligations, @Nullable List<String> permissions,
                              @Nullable Integer minAgents, @Nullable Integer maxAgents) {
            this(obligations, permissions, minAgents, maxAgents, null, null);
        }

        public RolePolicySpec(@Nullable List<String> obligations, @Nullable List<String> permissions,
                              @Nullable Integer minAgents, @Nullable Integer maxAgents,
                              @Nullable String vocabulary) {
            this(obligations, permissions, minAgents, maxAgents, vocabulary, null);
        }

        public RoleSpec toRoleSpec(String roleName) {
            var policies = new ArrayList<RolePolicy>();
            if (obligations != null) {
                for (var o : obligations) {
                    policies.add(new RolePolicy(o, PolicyKind.OBLIGATION, Map.of()));
                }
            }
            if (permissions != null) {
                for (var p : permissions) {
                    policies.add(new RolePolicy(p, PolicyKind.PERMISSION, Map.of()));
                }
            }
            return new RoleSpec(
                    roleName, vocabulary != null ? vocabulary : roleName,
                    minAgents != null ? minAgents : 0,
                    maxAgents != null ? maxAgents : -1,
                    incompatibleWith != null ? new java.util.LinkedHashSet<>(incompatibleWith) : null,
                    policies,
                    obligations != null ? obligations : List.of(),
                    permissions != null ? permissions : List.of());
        }
    }
}
