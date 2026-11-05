package io.casehub.agency.core.yaml.registry;

import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.agency.core.yaml.spec.CommunicationModeSpec;

public class CommunicationModeSpecRegistry {

    public CommunicationMode resolve(CommunicationModeSpec spec) {
        return switch (spec) {
            case CommunicationModeSpec.Stigmergy s -> CommunicationMode.STIGMERGY;
            case CommunicationModeSpec.DirectMessaging d -> CommunicationMode.DIRECT_MESSAGING;
            case CommunicationModeSpec.Broadcast b -> CommunicationMode.BROADCAST;
            case CommunicationModeSpec.GroupChannel g -> CommunicationMode.BROADCAST;
        };
    }
}
