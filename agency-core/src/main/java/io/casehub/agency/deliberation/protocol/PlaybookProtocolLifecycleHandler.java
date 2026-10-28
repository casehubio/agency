package io.casehub.agency.deliberation.protocol;

import io.casehub.platform.coordination.protocol.PlaybookProtocolEvaluator;
import io.casehub.platform.coordination.protocol.ProtocolInstanceKey;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ProtocolLifecycleContext;
import io.casehub.qhorus.api.spi.ProtocolLifecycleHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class PlaybookProtocolLifecycleHandler implements ProtocolLifecycleHandler {

    private static final String NEGOTIATION = "NEGOTIATION";
    private static final String TEMPLATE = "protocol-templates/negotiation.protocol.yaml";

    private final Instance<PlaybookProtocolEvaluator> evaluatorInstance;

    @Inject
    public PlaybookProtocolLifecycleHandler(Instance<PlaybookProtocolEvaluator> evaluatorInstance) {
        this.evaluatorInstance = evaluatorInstance;
    }

    @Override
    public boolean supports(String protocolName) {
        return NEGOTIATION.equals(protocolName) && !evaluatorInstance.isUnsatisfied();
    }

    @Override
    public void onInit(ProtocolLifecycleContext context) {
        PlaybookProtocolEvaluator evaluator = evaluatorInstance.get();
        Map<String, Object> statesMap = loadTemplate();
        evaluator.register(context.protocolName(), statesMap);

        if (context.recovered()) {
            replayHistory(context, evaluator);
        }
    }

    @Override
    public void onDestroy(UUID channelId, String protocolName) {
        if (!evaluatorInstance.isUnsatisfied()) {
            evaluatorInstance.get().evict(
                    new ProtocolInstanceKey(protocolName, channelId.toString()));
        }
    }

    private void replayHistory(ProtocolLifecycleContext context,
                               PlaybookProtocolEvaluator evaluator) {
        var key = new ProtocolInstanceKey(
                context.protocolName(), context.channelId().toString());
        for (MessageView msg : context.messageHistory()) {
            evaluator.evaluate(key, msg.type().name(), Map.of(
                    "sender", msg.sender(),
                    "correlationId", msg.correlationId() != null ? msg.correlationId() : ""));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadTemplate() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(TEMPLATE)) {
            if (is == null) {
                throw new IllegalStateException("Protocol template not found: " + TEMPLATE);
            }
            return new Yaml().load(is);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read protocol template: " + TEMPLATE, e);
        }
    }
}
