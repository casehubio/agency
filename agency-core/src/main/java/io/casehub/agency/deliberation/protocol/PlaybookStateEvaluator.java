package io.casehub.agency.deliberation.protocol;

import io.casehub.platform.coordination.protocol.PlaybookProtocolEvaluator;
import io.casehub.platform.coordination.protocol.ProtocolAdvisory;
import io.casehub.platform.coordination.protocol.ProtocolInstanceKey;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PlaybookStateEvaluator implements ProtocolStateEvaluator {

    private final PlaybookProtocolEvaluator playbookEvaluator;
    private final String protocolName;

    public PlaybookStateEvaluator(PlaybookProtocolEvaluator playbookEvaluator,
                                  String protocolName) {
        this.playbookEvaluator = Objects.requireNonNull(playbookEvaluator);
        this.protocolName = Objects.requireNonNull(protocolName);
    }

    @Override
    public List<DispatchAdvisory> evaluate(ProtocolContext context) {
        var key = new ProtocolInstanceKey(protocolName, context.channelId().toString());
        String event = context.incomingType().name();
        Map<String, Object> evalContext = buildContext(context);
        List<ProtocolAdvisory> advisories = playbookEvaluator.evaluate(key, event, evalContext);
        return AdvisoryMapper.toDispatchAdvisories(advisories, protocolName);
    }

    private Map<String, Object> buildContext(ProtocolContext context) {
        return Map.of(
                "sender", context.sender(),
                "correlationId", context.correlationId() != null ? context.correlationId() : "",
                "participants", context.protocolParticipants());
    }
}
