package io.casehub.agency.core.yaml.registry;

import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.agency.core.yaml.spec.CommunicationModeSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationModeSpecRegistryTest {

    private final CommunicationModeSpecRegistry registry = new CommunicationModeSpecRegistry();

    @Test
    void stigmergy_resolves() {
        assertThat(registry.resolve(new CommunicationModeSpec.Stigmergy()))
                .isEqualTo(CommunicationMode.STIGMERGY);
    }

    @Test
    void directMessaging_resolves() {
        assertThat(registry.resolve(new CommunicationModeSpec.DirectMessaging()))
                .isEqualTo(CommunicationMode.DIRECT_MESSAGING);
    }

    @Test
    void broadcast_resolves() {
        assertThat(registry.resolve(new CommunicationModeSpec.Broadcast()))
                .isEqualTo(CommunicationMode.BROADCAST);
    }

    @Test
    void groupChannel_resolves() {
        assertThat(registry.resolve(new CommunicationModeSpec.GroupChannel()))
                .isEqualTo(CommunicationMode.BROADCAST);
    }
}
