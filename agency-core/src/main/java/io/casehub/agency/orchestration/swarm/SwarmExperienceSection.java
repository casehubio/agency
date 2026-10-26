package io.casehub.agency.orchestration.swarm;

import io.casehub.api.spi.routing.ExperienceAnalyser;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SwarmExperienceSection implements SwarmPromptSection {

    private final ExperienceReplayConfig config;

    public SwarmExperienceSection(ExperienceReplayConfig config) {
        this.config = config;
    }

    @Override
    public String contribute(SwarmContext context) {
        var experiences = context.experiences();
        if (experiences == null || experiences.isEmpty()) return "";

        var filtered = experiences.stream()
                .filter(e -> e.similarityScore() >= config.minSimilarity())
                .sorted(Comparator.comparingDouble(RetrievedExperience::similarityScore).reversed())
                .limit(config.maxTraces())
                .toList();

        if (filtered.isEmpty()) return "";

        var agentIds = filtered.stream()
                .flatMap(e -> e.planTrace().stream())
                .map(ExperiencePlanStep::workerName)
                .collect(Collectors.toSet());

        var successRates = ExperienceAnalyser.workerSuccessRates(
                filtered, agentIds, s -> true, ExperienceAnalyser.DEFAULT_OUTCOME_WEIGHTS);

        var sb = new StringBuilder("## Historical Coordination Patterns\n\n");
        for (var exp : filtered) {
            sb.append("### Experience (similarity: %.2f, outcome: %s)\n".formatted(
                    exp.similarityScore(), exp.outcome()));
            appendTeamPatterns(sb, exp);
            sb.append("\n");
        }

        if (!successRates.isEmpty()) {
            sb.append("### Agent Success Rates\n");
            successRates.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .forEach(e -> sb.append("- %s: %.0f%%\n".formatted(e.getKey(), e.getValue() * 100)));
        }

        return sb.toString();
    }

    private void appendTeamPatterns(StringBuilder sb, RetrievedExperience exp) {
        var trace = exp.planTrace();
        if (trace == null || trace.isEmpty()) return;

        var workers = trace.stream()
                .map(ExperiencePlanStep::workerName)
                .distinct()
                .toList();
        sb.append("- Team: %s\n".formatted(String.join(", ", workers)));

        var capabilities = trace.stream()
                .collect(Collectors.groupingBy(ExperiencePlanStep::workerName,
                        Collectors.mapping(ExperiencePlanStep::capabilityName, Collectors.toSet())));
        capabilities.forEach((worker, caps) ->
                sb.append("  - %s: %s\n".formatted(worker, String.join(", ", caps))));
    }
}
