package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationModeSpecTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Test
    void stigmergy_deserialises() throws Exception {
        var spec = yaml.readValue("type: stigmergy", CommunicationModeSpec.class);
        assertThat(spec).isInstanceOf(CommunicationModeSpec.Stigmergy.class);
    }

    @Test
    void directMessaging_deserialises() throws Exception {
        var spec = yaml.readValue("type: direct-messaging", CommunicationModeSpec.class);
        assertThat(spec).isInstanceOf(CommunicationModeSpec.DirectMessaging.class);
    }

    @Test
    void broadcast_deserialises() throws Exception {
        var spec = yaml.readValue("type: broadcast", CommunicationModeSpec.class);
        assertThat(spec).isInstanceOf(CommunicationModeSpec.Broadcast.class);
    }

    @Test
    void groupChannel_deserialises() throws Exception {
        var spec = yaml.readValue("type: group-channel", CommunicationModeSpec.class);
        assertThat(spec).isInstanceOf(CommunicationModeSpec.GroupChannel.class);
    }
}
