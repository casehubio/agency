package io.casehub.agency.core.yaml.deployment;

import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.quarkus.builder.item.SimpleBuildItem;

import java.util.List;

public final class TopologyBuildItem extends SimpleBuildItem {
    private final List<TopologySpec.Hive> hiveTopologies;

    public TopologyBuildItem(List<TopologySpec.Hive> hiveTopologies) {
        this.hiveTopologies = List.copyOf(hiveTopologies);
    }

    public List<TopologySpec.Hive> hiveTopologies() {
        return hiveTopologies;
    }
}
