package io.casehub.agency.core.yaml.runtime;

import io.casehub.agency.core.yaml.spec.AvatarDefinition;

public class AvatarConfigBean {

    private final AvatarDefinition definition;
    private final String json;

    public AvatarConfigBean(AvatarDefinition definition, String json) {
        this.definition = definition;
        this.json = json;
    }

    public AvatarDefinition definition() {
        return definition;
    }

    public String json() {
        return json;
    }
}
