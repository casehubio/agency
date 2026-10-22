package io.casehub.agency.core.yaml.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentConstraint;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.ConstraintSeverity;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.GoalPriority;
import io.casehub.eidos.api.Visibility;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DescriptorLoader {

    @SuppressWarnings("unchecked")
    public static List<AgentDescriptor> load(String scenario) {
        String path = "/examples/" + scenario + "/descriptors.yaml";
        try (InputStream is = DescriptorLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Descriptors not found: " + path);
            }
            var mapper = YamlMappers.create();
            List<Map<String, Object>> rawList = mapper.readValue(is, List.class);
            var result = new ArrayList<AgentDescriptor>();
            for (var raw : rawList) {
                result.add(buildDescriptor(raw));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static AgentDescriptor buildDescriptor(Map<String, Object> raw) {
        var name = (String) raw.get("name");
        var briefing = raw.get("briefing") != null ? raw.get("briefing").toString().strip() : "";

        var builder = AgentDescriptor.builder()
                .agentId(name)
                .name(name)
                .tenancyId("showcase")
                .briefing(briefing);

        if (raw.get("slot") != null) builder.slot((String) raw.get("slot"));
        if (raw.get("slotVocabulary") != null) builder.slotVocabulary((String) raw.get("slotVocabulary"));
        if (raw.get("dispositionVocabulary") != null) builder.dispositionVocabulary((String) raw.get("dispositionVocabulary"));
        if (raw.get("provider") != null) builder.provider((String) raw.get("provider"));
        if (raw.get("modelFamily") != null) builder.modelFamily((String) raw.get("modelFamily"));
        if (raw.get("modelVersion") != null) builder.modelVersion((String) raw.get("modelVersion"));
        if (raw.get("archetype") != null) builder.archetype((String) raw.get("archetype"));

        if (raw.get("capabilities") instanceof List<?> capList) {
            var capabilities = new ArrayList<AgentCapability>();
            for (var capRaw : capList) {
                if (capRaw instanceof Map<?, ?> capMap) {
                    var capName = (String) capMap.get("name");
                    var tags = capMap.get("tags") instanceof List<?> t
                            ? t.stream().map(Object::toString).toList() : List.<String>of();
                    Double qualityHint = null;
                    if (capMap.get("qualityHint") instanceof Number qh) {
                        qualityHint = qh.doubleValue();
                    }
                    capabilities.add(AgentCapability.builder()
                            .name(capName)
                            .qualityHint(qualityHint)
                            .tags(tags)
                            .build());
                }
            }
            builder.capabilities(capabilities);
        }

        if (raw.get("disposition") instanceof Map<?, ?> dispMap) {
            builder.disposition(AgentDisposition.builder()
                    .socialOrient(dispValues(dispMap, "socialOrient"))
                    .ruleFollowing(dispValues(dispMap, "ruleFollowing"))
                    .riskAppetite(dispValues(dispMap, "riskAppetite"))
                    .autonomy(dispValues(dispMap, "autonomy"))
                    .conflictMode(dispValues(dispMap, "conflictMode"))
                    .delegation(Boolean.TRUE.equals(dispMap.get("delegation")))
                    .build());
        }

        if (raw.get("goals") instanceof List<?> goalList) {
            var goals = new ArrayList<AgentGoal>();
            for (var goalRaw : goalList) {
                if (goalRaw instanceof Map<?, ?>) {
                    var goalMap = (Map<String, Object>) goalRaw;
                    goals.add(new AgentGoal(
                            (String) goalMap.get("name"),
                            (String) goalMap.get("description"),
                            GoalPriority.valueOf(((String) goalMap.getOrDefault("priority", "SECONDARY")).toUpperCase()),
                            Visibility.valueOf(((String) goalMap.getOrDefault("visibility", "PUBLIC")).toUpperCase()),
                            List.of(), Map.of()));
                }
            }
            builder.goals(goals);
        }

        if (raw.get("constraints") instanceof List<?> conList) {
            var constraints = new ArrayList<AgentConstraint>();
            for (var conRaw : conList) {
                if (conRaw instanceof Map<?, ?>) {
                    var conMap = (Map<String, Object>) conRaw;
                    constraints.add(new AgentConstraint(
                            (String) conMap.get("name"),
                            (String) conMap.get("description"),
                            Visibility.valueOf(((String) conMap.getOrDefault("visibility", "PUBLIC")).toUpperCase()),
                            ConstraintSeverity.valueOf(((String) conMap.getOrDefault("severity", "SOFT")).toUpperCase())));
                }
            }
            builder.constraints(constraints);
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static List<DispositionValue> dispValues(Map<?, ?> map, String key) {
        var val = map.get(key);
        if (val == null) {
            return List.of();
        }
        if (val instanceof String s) {
            return List.of(DispositionValue.of(s));
        }
        if (val instanceof List<?> list) {
            var result = new ArrayList<DispositionValue>();
            for (var item : list) {
                if (item instanceof Map<?, ?> m) {
                    var term = (String) m.get("term");
                    var weight = m.get("weight") instanceof Number n ? n.doubleValue() : 1.0;
                    result.add(new DispositionValue(term, weight));
                }
            }
            return List.copyOf(result);
        }
        return List.of(DispositionValue.of(val.toString()));
    }
}
