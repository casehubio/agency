package io.casehub.agency.core.yaml.registry;

import io.casehub.api.model.coordination.TopologyType;
import io.casehub.agency.core.yaml.spec.TopologySpec;

public class TopologySpecRegistry {

    public TopologyType resolve(TopologySpec spec) {
        TopologyType defaultType = switch (spec) {
            case TopologySpec.Swarm s -> TopologyType.DISTRIBUTED;
            case TopologySpec.Hive h -> TopologyType.CENTRALISED;
            case TopologySpec.Mesh m -> TopologyType.DISTRIBUTED;
            case TopologySpec.Many m -> TopologyType.DISTRIBUTED;
        };

        String authority = spec.authority();
        if (authority == null) return defaultType;

        return switch (authority) {
            case "centralised" -> TopologyType.CENTRALISED;
            case "distributed" -> TopologyType.DISTRIBUTED;
            case "hybrid" -> TopologyType.HYBRID;
            case "hierarchical" -> TopologyType.HIERARCHICAL;
            case "graduated" -> TopologyType.GRADUATED;
            default -> throw new IllegalArgumentException(
                    "Unknown authority model: " + authority);
        };
    }
}
