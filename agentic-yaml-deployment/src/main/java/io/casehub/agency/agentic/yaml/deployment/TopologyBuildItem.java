package io.casehub.agency.core.yaml.deployment;

import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.quarkus.builder.item.SimpleBuildItem;

import java.util.List;

public final class TopologyBuildItem extends SimpleBuildItem {
    private final List<TopologySpec.Hive> hiveTopologies;
    private final List<TopologySpec.Distributed> distributedTopologies;

    public TopologyBuildItem(List<TopologySpec.Hive> hiveTopologies,
                              List<TopologySpec.Distributed> distributedTopologies) {
        this.hiveTopologies = List.copyOf(hiveTopologies);
        this.distributedTopologies = List.copyOf(distributedTopologies);
    }

    public TopologyBuildItem(List<TopologySpec.Hive> hiveTopologies) {
        this(hiveTopologies, List.of());
    }

    public List<TopologySpec.Hive> hiveTopologies() {
        return hiveTopologies;
    }

    public List<TopologySpec.Distributed> distributedTopologies() {
        return distributedTopologies;
    }
}
