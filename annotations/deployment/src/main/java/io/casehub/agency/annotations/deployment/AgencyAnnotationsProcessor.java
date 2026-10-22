package io.casehub.agency.annotations.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class AgencyAnnotationsProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("casehub-agency-annotations");
    }
}
