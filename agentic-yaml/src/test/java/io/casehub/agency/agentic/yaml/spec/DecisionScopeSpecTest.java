package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionScopeSpecTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Test
    void reactive_deserialises() throws Exception {
        var spec = yaml.readValue("type: reactive", DecisionScopeSpec.class);
        assertThat(spec).isInstanceOf(DecisionScopeSpec.Reactive.class);
    }

    @Test
    void taskAdaptation_deserialises() throws Exception {
        var spec = yaml.readValue("type: task-adaptation", DecisionScopeSpec.class);
        assertThat(spec).isInstanceOf(DecisionScopeSpec.TaskAdaptation.class);
    }

    @Test
    void fullAutonomy_deserialises() throws Exception {
        var spec = yaml.readValue("type: full-autonomy", DecisionScopeSpec.class);
        assertThat(spec).isInstanceOf(DecisionScopeSpec.FullAutonomy.class);
    }
}
