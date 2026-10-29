package io.casehub.agency.core.yaml.runtime;

public class AgencySpecsBean {

    private final String json;

    public AgencySpecsBean(String json) {
        this.json = json;
    }

    public String json() {
        return json;
    }
}
