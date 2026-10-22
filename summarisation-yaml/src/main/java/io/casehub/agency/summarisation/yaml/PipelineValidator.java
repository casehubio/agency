package io.casehub.agency.summarisation.yaml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PipelineValidator {

    public record ValidationError(String levelName, String message, Level level) {
        public enum Level { ERROR, WARNING, INFO }

        static ValidationError error(String levelName, String message) {
            return new ValidationError(levelName, message, Level.ERROR);
        }

        static ValidationError info(String levelName, String message) {
            return new ValidationError(levelName, message, Level.INFO);
        }
    }

    public List<ValidationError> validate(PipelineDefinition definition,
                                           SummariserRegistry registry) {
        var errors = new ArrayList<ValidationError>();

        if (definition.levels().isEmpty()) {
            errors.add(ValidationError.error("pipeline",
                    "Pipeline must have at least one level"));
            return errors;
        }

        for (var level : definition.levels()) {
            validateLevel(level, registry, errors);
        }

        return errors;
    }

    private void validateLevel(LevelDefinition level,
                                SummariserRegistry registry,
                                List<ValidationError> errors) {
        if (level.grouping() == null) {
            errors.add(ValidationError.error(level.name(),
                    "Level requires a grouping definition"));
        }

        var type = level.summariser().type();
        if (!registry.hasType(type)) {
            errors.add(ValidationError.error(level.name(),
                    "Unknown summariser type: " + type));
            return;
        }

        var config = level.summariser().config();
        switch (type) {
            case "phase-detect" -> validatePhaseDetect(level.name(), config, errors);
            case "threshold-classify" -> validateThresholdClassify(level.name(), config, errors);
            case "count" -> validateCount(level.name(), config, errors);
            default -> {}
        }
    }

    @SuppressWarnings("unchecked")
    private void validatePhaseDetect(String levelName, Map<String, Object> config,
                                      List<ValidationError> errors) {
        var initial = (String) config.get("initial");
        if (initial == null) {
            errors.add(ValidationError.error(levelName,
                    "phase-detect requires 'initial' state"));
            return;
        }

        var states = (List<String>) config.get("states");
        var transitions = (List<Map<String, Object>>) config.get("transitions");
        if (states == null || transitions == null) return;

        var reachable = new HashSet<String>();
        reachable.add(initial);
        var queue = new LinkedList<String>();
        queue.add(initial);
        while (!queue.isEmpty()) {
            var current = queue.poll();
            for (var t : transitions) {
                if (current.equals(t.get("from"))) {
                    var to = (String) t.get("to");
                    if (reachable.add(to)) {
                        queue.add(to);
                    }
                }
            }
        }

        for (var state : states) {
            if (!reachable.contains(state)) {
                errors.add(ValidationError.error(levelName,
                        "phase-detect state '" + state + "' is unreachable from initial '" + initial + "'"));
            }
        }

        var terminalStates = new HashSet<>(states);
        for (var t : transitions) {
            terminalStates.remove(t.get("from"));
        }
        for (var terminal : terminalStates) {
            if (!terminal.equals(initial) || !transitions.isEmpty()) {
                errors.add(ValidationError.info(levelName,
                        "phase-detect state '" + terminal + "' is terminal (no outbound transitions)"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateThresholdClassify(String levelName, Map<String, Object> config,
                                            List<ValidationError> errors) {
        var rules = (List<?>) config.get("rules");
        if (rules == null || rules.isEmpty()) {
            errors.add(ValidationError.error(levelName,
                    "threshold-classify requires non-empty 'rules'"));
        }
    }

    private void validateCount(String levelName, Map<String, Object> config,
                                List<ValidationError> errors) {
        if (config.get("category-field") == null) {
            errors.add(ValidationError.error(levelName,
                    "count requires 'category-field'"));
        }
    }
}
