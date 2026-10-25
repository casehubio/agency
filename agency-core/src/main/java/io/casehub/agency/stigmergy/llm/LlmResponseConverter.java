package io.casehub.agency.stigmergy.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.spi.observation.Observation;
import io.casehub.api.spi.observation.RuleAction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LlmResponseConverter {

    private static final Logger LOG = Logger.getLogger(LlmResponseConverter.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmResponseConverter() {}

    public static List<Observation> toObservations(LlmObservationResponse response) {
        var result = new ArrayList<Observation>();
        for (var raw : response.observations()) {
            var details = new HashMap<String, JsonNode>();
            for (var entry : raw.details().entrySet()) {
                details.put(entry.getKey(), MAPPER.valueToTree(entry.getValue()));
            }
            details.put("evidence", MAPPER.valueToTree(raw.evidence()));
            details.put("derivedFrom", MAPPER.valueToTree(raw.derivedFrom()));
            details.put("reasoning", MAPPER.valueToTree(raw.reasoning()));
            result.add(new Observation(raw.patternId(), raw.confidence(),
                Map.copyOf(details), Instant.now()));
        }
        return List.copyOf(result);
    }

    public static List<RecommendedAction> toRecommendedActions(LlmActionResponse response) {
        var result = new ArrayList<RecommendedAction>();
        for (var rec : response.actions()) {
            var action = convertAction(rec);
            if (action != null) {
                result.add(new RecommendedAction(action, rec.justifyingPatternIds(),
                    rec.confidence()));
            }
        }
        return List.copyOf(result);
    }

    private static RuleAction convertAction(LlmRecommendation rec) {
        return switch (rec.actionType()) {
            case "DepositSignal" -> convertDepositSignal(rec);
            case "WriteContext" -> convertWriteContext(rec);
            default -> {
                LOG.log(Level.WARNING, "Unknown action type: {0}", rec.actionType());
                yield null;
            }
        };
    }

    private static RuleAction convertDepositSignal(LlmRecommendation rec) {
        var params = rec.parameters();
        var name = asString(params.get("name"));
        var strength = asDouble(params.get("strength"));
        if (name == null || strength == null) {
            LOG.log(Level.WARNING,
                "DepositSignal missing required params (name, strength): {0}", params);
            return null;
        }
        return new RuleAction.DepositSignal(name, strength, null);
    }

    private static RuleAction convertWriteContext(LlmRecommendation rec) {
        var params = rec.parameters();
        var key = asString(params.get("key"));
        var value = params.get("value");
        if (key == null || value == null) {
            LOG.log(Level.WARNING,
                "WriteContext missing required params (key, value): {0}", params);
            return null;
        }
        return new RuleAction.WriteContext(key, MAPPER.valueToTree(value));
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private static Double asDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }
}
