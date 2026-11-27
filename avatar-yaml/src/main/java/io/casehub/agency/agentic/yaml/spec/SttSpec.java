package io.casehub.agency.core.yaml.spec;

public record SttSpec(String type,
                      @com.fasterxml.jackson.annotation.JsonProperty("endpointDetection")
                      @com.fasterxml.jackson.annotation.JsonAlias("endpoint-detection")
                      @org.jspecify.annotations.Nullable EndpointDetectionSpec endpointDetection) {

    public SttSpec {
        java.util.Objects.requireNonNull(type, "type");
    }

    public SttSpec(String type) {
        this(type, null);
    }
}
