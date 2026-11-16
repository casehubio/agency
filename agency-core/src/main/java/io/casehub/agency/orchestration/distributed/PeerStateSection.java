package io.casehub.agency.orchestration.distributed;

import io.casehub.agency.orchestration.loop.DecisionContext;
import io.casehub.agency.orchestration.loop.LoopPromptSection;

import java.util.Map;
import java.util.function.Supplier;

public class PeerStateSection implements LoopPromptSection {

    private final Supplier<Map<String, String>> peerStates;

    public PeerStateSection(Supplier<Map<String, String>> peerStates) {
        this.peerStates = peerStates;
    }

    @Override
    public String contribute(DecisionContext context) {
        var states = peerStates.get();
        if (states == null || states.isEmpty()) return "";
        var sb = new StringBuilder("## Peer States\n\n");
        for (var entry : states.entrySet()) {
            if (!entry.getKey().equals(context.agentId())) {
                sb.append("- **").append(entry.getKey()).append("**: ").append(entry.getValue()).append('\n');
            }
        }
        return sb.toString();
    }
}
