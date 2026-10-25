package io.casehub.agency.core.yaml.llm;

import io.casehub.neocortex.cognition.core.CognitionCore;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelMemory;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.strategy.StrategyLearningConfig;
import io.casehub.neocortex.cognition.strategy.StrategyLearningOrchestrator;
import io.casehub.neocortex.cognition.strategy.StrategyMemory;
import io.casehub.neocortex.cognition.usermodel.UserModelOrchestrator;
import io.casehub.neocortex.cognition.usermodel.UserProfileMemory;
import io.casehub.neocortex.cognition.drive.AffiliationDrive;
import io.casehub.neocortex.cognition.drive.AutonomyDrive;
import io.casehub.neocortex.cognition.drive.CompetenceDrive;
import io.casehub.neocortex.cognition.drive.CuriosityDrive;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveComposer;
import io.casehub.neocortex.cognition.drive.DriveIntensity;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.DriveSource;
import io.casehub.neocortex.cognition.goal.DriveGoalMapper;
import io.casehub.neocortex.cognition.goal.GoalProposalConfig;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognition.narrative.DerivedTheme;
import io.casehub.neocortex.cognition.narrative.IndividualEpisode;
import io.casehub.neocortex.cognition.narrative.NarrativeFragment;
import io.casehub.neocortex.cognition.narrative.NarrativeMemory;
import io.casehub.neocortex.cognition.narrative.NarrativeOrchestrator;
import io.casehub.neocortex.cognition.narrative.NarrativeScope;
import io.casehub.neocortex.cognition.narrative.NarrativeState;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.agency.core.yaml.compiler.CompiledCognition;
import io.casehub.agency.social.cognition.MemoryHygieneSpiAdapter;
import io.casehub.agency.social.memory.ArousalScorer;
import io.casehub.agency.social.memory.CompositeConfidenceScorer;
import io.casehub.agency.social.memory.MemoryHygieneOrchestrator;
import io.casehub.agency.social.memory.RetentionConfig;
import io.casehub.agency.social.memory.SurpriseScorer;
import io.casehub.agency.social.memory.WeightedScorer;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.memory.cbr.ScopeDecay;
import io.casehub.neocortex.memory.cbr.TemporalDecay;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrRecordStore;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CognitionStack {

    private static final System.Logger LOG =
            System.getLogger(CognitionStack.class.getName());

    private final CognitionCore core;
    private final Stage stage;
    private final NarrativeMemory narrativeMemory;
    private final @Nullable AgentProvider agentProvider;
    private final @Nullable CbrRecordStore cbrStore;
    private final @Nullable StrategyLearningConfig strategyConfig;
    private final ConcurrentHashMap<String, Boolean> primedAgents = new ConcurrentHashMap<>();

    public enum Stage {
        BASELINE, SIGNALS, REAL_DRIVES, NARRATIVE, FULL
    }

    CognitionStack(CognitionCore core, Stage stage,
                   NarrativeMemory narrativeMemory,
                   @Nullable AgentProvider agentProvider,
                   @Nullable CbrRecordStore cbrStore,
                   @Nullable StrategyLearningConfig strategyConfig) {
        this.core = core;
        this.stage = stage;
        this.narrativeMemory = narrativeMemory;
        this.agentProvider = agentProvider;
        this.cbrStore = cbrStore;
        this.strategyConfig = strategyConfig;
    }

    public static CognitionStack from(CompiledCognition config,
                                       @Nullable AgentProvider agentProvider,
                                       Stage stage) {
        var mood = new MoodOrchestrator(config.mood());
        var cbrStore = new InMemoryCbrRecordStore();
        var narrativeMemory = new NarrativeMemory(cbrStore, config.narrative());
        var narrative = new NarrativeOrchestrator(narrativeMemory);
        var composer = new DriveComposer();

        UserModelOrchestrator userModel = null;
        MentalModelOrchestrator mentalModel = null;
        StrategyLearningOrchestrator strategy = null;

        if (agentProvider != null && stage.ordinal() >= Stage.SIGNALS.ordinal()) {
            userModel = new UserModelOrchestrator(
                    new UserProfileMemory(cbrStore, config.userModel()),
                    config.userModel(), null);
            mentalModel = new MentalModelOrchestrator(
                    new MentalModelMemory(cbrStore, config.mentalModel()),
                    config.mentalModel(), Clock.systemUTC(), null);
        }

        DriveOrchestrator drives;
        GoalProposalOrchestrator goals = null;
        io.casehub.neocortex.cognition.memory.MemoryHygieneOrchestrator hygieneAdapter = null;
        if (agentProvider != null && stage.ordinal() >= Stage.REAL_DRIVES.ordinal()) {
            strategy = new StrategyLearningOrchestrator(
                    new StrategyMemory(cbrStore, config.strategyLearning()),
                    (agentId, tenantId, since, maxEntries) -> List.of(),
                    config.strategyLearning(), Clock.systemUTC(), null);
            var memoryHygiene = new MemoryHygieneOrchestrator(
                    cbrStore,
                    new CompositeConfidenceScorer(List.of(
                            new WeightedScorer(new ArousalScorer(), 0.5),
                            new WeightedScorer(new SurpriseScorer(), 0.5))),
                    new TemporalDecay.HalfLife(java.time.Duration.ofDays(365)),
                    new ScopeDecay.Step(1.0),
                    null,
                    config.strategyLearning().memoryDomain(),
                    List.of(config.strategyLearning().engagementCaseType()),
                    RetentionConfig.DEFAULT,
                    10,
                    0.7,
                    event -> {});
            hygieneAdapter = new MemoryHygieneSpiAdapter(memoryHygiene);
            var curiosityDrive = new CuriosityDrive(hygieneAdapter);
            var competence = new CompetenceDrive(strategy);
            var affiliation = new AffiliationDrive(userModel, 0.3,
                    java.time.Duration.ofHours(1));
            var autonomy = new AutonomyDrive(mentalModel, 0.5);
            drives = new DriveOrchestrator(curiosityDrive, competence,
                    affiliation, autonomy, mood, composer, config.drive());

            if (stage.ordinal() >= Stage.FULL.ordinal()) {
                var goalConfig = new GoalProposalConfig(
                        config.goalProposal().proposalThreshold(),
                        config.goalProposal().relevanceThreshold(),
                        config.goalProposal().maxDriveGoals(),
                        config.goalProposal().staleAfter(),
                        java.time.Duration.ofSeconds(30),
                        config.goalProposal().failureAbandonmentThreshold());
                var mapperList = List.<DriveGoalMapper>of(
                        (agentId, tenantId, intensity) -> null,
                        (agentId, tenantId, intensity) -> null,
                        (agentId, tenantId, intensity) -> null,
                        (agentId, tenantId, intensity) -> null);
                goals = new GoalProposalOrchestrator(drives,
                        mapperList,
                        null,
                        Optional.empty(),
                        null,
                        null,
                        null,
                        goalConfig,
                        config.goalEscalation(),
                        Clock.systemUTC());
            }
        } else {
            DriveSource baseline = (agentId, tenantId) ->
                    new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
            drives = new DriveOrchestrator(
                    baseline, baseline, baseline, baseline,
                    mood, composer, config.drive());
        }

        var core = new CognitionCore(mood, drives, userModel, mentalModel,
                strategy, narrative, goals, hygieneAdapter);
        return new CognitionStack(core, stage, narrativeMemory, agentProvider,
                cbrStore, strategy != null ? config.strategyLearning() : null);
    }

    public static CognitionStack from(CompiledCognition config,
                                       @Nullable AgentProvider agentProvider) {
        return from(config, agentProvider, Stage.SIGNALS);
    }

    public CognitionCore core() { return core; }
    public Stage stage() { return stage; }

    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor) {
        primeIfNeeded(agentId, tenantId, descriptor);
        core.tick(agentId, tenantId, descriptor, (a, t) -> Set.of());
    }

    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor,
                     Set<String> activeSubjects) {
        primeIfNeeded(agentId, tenantId, descriptor);
        core.tick(agentId, tenantId, descriptor, (a, t) -> activeSubjects);
    }

    private void primeIfNeeded(String agentId, String tenantId,
                                @Nullable AgentDescriptor descriptor) {
        if (stage.ordinal() < Stage.REAL_DRIVES.ordinal()) return;
        if (descriptor == null || cbrStore == null || strategyConfig == null) return;
        if (primedAgents.putIfAbsent(agentId + ":" + tenantId, true) != null) return;

        var constraints = descriptor.constraints();
        if (constraints == null || constraints.isEmpty()) return;

        for (int i = 0; i < strategyConfig.minCasesForReflection(); i++) {
            var features = buildSyntheticFeatures(descriptor, i, strategyConfig.minCasesForReflection());
            var summary = "Synthetic interaction " + (i + 1) + " derived from "
                    + constraints.getFirst().description();
            var cbrCase = new io.casehub.neocortex.memory.cbr.CbrFeatureRecord(
                    summary, "-", null, null, features, null, agentId);
            cbrStore.store(cbrCase, strategyConfig.engagementCaseType(),
                    agentId, strategyConfig.memoryDomain(), tenantId, null,
                    io.casehub.platform.api.path.Path.root());
        }
    }

    private static Map<String, io.casehub.neocortex.memory.cbr.FeatureValue> buildSyntheticFeatures(
            AgentDescriptor descriptor, int index, int count) {
        double progress = count > 1 ? (double) index / (count - 1) : 0.5;

        var features = new java.util.LinkedHashMap<String, io.casehub.neocortex.memory.cbr.FeatureValue>();
        features.put("subjectId", io.casehub.neocortex.memory.cbr.FeatureValue.string("synthetic"));
        features.put("agentId", io.casehub.neocortex.memory.cbr.FeatureValue.string(descriptor.name()));
        features.put("conversationTimestamp",
                io.casehub.neocortex.memory.cbr.FeatureValue.number(
                        (double) (Instant.now().toEpochMilli() - (count - index) * 3600_000L)));
        features.put("turnCount", io.casehub.neocortex.memory.cbr.FeatureValue.number(4.0 + index));
        features.put("avgResponseLength", io.casehub.neocortex.memory.cbr.FeatureValue.number(80 + progress * 170));
        features.put("continuationRate", io.casehub.neocortex.memory.cbr.FeatureValue.number(0.6 + progress * 0.25));
        features.put("meanAffectShift", io.casehub.neocortex.memory.cbr.FeatureValue.number(-0.05 + progress * 0.25));

        double formality = 0.5;
        double verbosity = 0.5;
        if (descriptor.constraints() != null) {
            for (var c : descriptor.constraints()) {
                var text = c.description().toLowerCase();
                if (text.contains("precision") || text.contains("exact") || text.contains("rigour")) {
                    formality = 0.8; verbosity = 0.3;
                } else if (text.contains("artistic") || text.contains("creative") || text.contains("expressive")) {
                    formality = 0.4; verbosity = 0.7;
                }
            }
        }
        features.put("avgSnapshot_verbosity", io.casehub.neocortex.memory.cbr.FeatureValue.number(verbosity));
        features.put("avgSnapshot_formality", io.casehub.neocortex.memory.cbr.FeatureValue.number(formality));
        features.put("avgSnapshot_initiative", io.casehub.neocortex.memory.cbr.FeatureValue.number(0.5));
        features.put("avgSnapshot_directness", io.casehub.neocortex.memory.cbr.FeatureValue.number(0.5));
        features.put("avgSnapshot_questionRate", io.casehub.neocortex.memory.cbr.FeatureValue.number(0.5));

        return Map.copyOf(features);
    }

    public List<CognitionPromptRenderer> promptSections() {
        return core.promptSections();
    }

    private static final String EPISODE_PROMPT = """
            You are analysing a conversation between historical figures. \
            Extract the most significant moment from the latest exchange.

            Respond ONLY with JSON:
            {"description":"One sentence describing what happened and why it matters",\
            "valence":0.5,\
            "tags":["tag1","tag2"]}

            valence: emotional significance from -1 (deeply negative) to +1 (deeply positive)
            tags: 2-4 thematic tags (e.g. "intellectual-recognition", "shared-vulnerability")""";

    private static final String THEME_PROMPT = """
            You are analysing a series of narrative episodes from a conversation \
            between historical figures. Derive the overarching theme.

            Respond ONLY with JSON:
            {"label":"kebab-case-theme-name",\
            "salience":0.7,\
            "tags":["tag1","tag2"],\
            "drives":{"CURIOSITY":0.3,"AFFILIATION":0.2}}

            label: short kebab-case theme identifier
            salience: how central this theme is [0,1]
            tags: 2-3 thematic tags
            drives: which motivational axes this theme relates to and how strongly [-1,+1]. \
            Valid axes: CURIOSITY, COMPETENCE, AFFILIATION, AUTONOMY. Only include relevant axes.""";

    public void updateNarrative(String agentId, String tenantId,
                                List<ConversationRunner.Turn> recentTurns) {
        if (stage.ordinal() < Stage.NARRATIVE.ordinal()) return;
        if (recentTurns.size() < 2) return;

        var now = Instant.now();
        var existing = narrativeMemory.load(agentId, tenantId);
        var fragments = new ArrayList<NarrativeFragment>();
        if (existing != null) fragments.addAll(existing.fragments());

        var latest = recentTurns.subList(
                Math.max(0, recentTurns.size() - 2), recentTurns.size());
        var episode = extractEpisode(latest, now);
        fragments.add(episode);

        var episodeCount = fragments.stream()
                .filter(f -> f instanceof IndividualEpisode).count();
        if (episodeCount >= 2 && episodeCount % 2 == 0) {
            var theme = deriveTheme(fragments, now);
            if (theme != null) {
                fragments.removeIf(f -> f instanceof DerivedTheme);
                fragments.add(theme);
            }
        }

        var state = new NarrativeState(agentId, tenantId,
                NarrativeScope.INDIVIDUAL, fragments, now,
                recentTurns.size());
        narrativeMemory.store(state);
    }

    private IndividualEpisode extractEpisode(List<ConversationRunner.Turn> turns,
                                              Instant now) {
        if (agentProvider == null) {
            return heuristicEpisode(turns, now);
        }
        try {
            var exchange = turns.stream()
                    .map(t -> t.speakerName() + ": " + truncate(t.dialogue(), 300))
                    .collect(Collectors.joining("\n\n"));
            var config = AgentSessionConfig.of(EPISODE_PROMPT, exchange);
            var json = invokeAndExtractJson(config);
            if (json == null) return heuristicEpisode(turns, now);

            var description = extractJsonString(json, "description");
            var valence = extractJsonDouble(json, "valence", 0.5);
            var tags = extractJsonStringArray(json, "tags");

            if (description.isBlank()) return heuristicEpisode(turns, now);

            return new IndividualEpisode(
                    UUID.randomUUID().toString(), now, now,
                    tags.isEmpty() ? List.of("exchange") : tags,
                    description, Math.clamp(valence, -1.0, 1.0), List.of());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "LLM episode extraction failed, using heuristic", e);
            return heuristicEpisode(turns, now);
        }
    }

    private @Nullable DerivedTheme deriveTheme(List<NarrativeFragment> fragments,
                                                Instant now) {
        if (agentProvider == null) return heuristicTheme(fragments, now);
        try {
            var episodeSummary = fragments.stream()
                    .filter(f -> f instanceof IndividualEpisode)
                    .map(f -> "- " + ((IndividualEpisode) f).description())
                    .collect(Collectors.joining("\n"));
            var config = AgentSessionConfig.of(THEME_PROMPT,
                    "Episodes so far:\n" + episodeSummary);
            var json = invokeAndExtractJson(config);
            if (json == null) return heuristicTheme(fragments, now);

            var label = extractJsonString(json, "label");
            var salience = extractJsonDouble(json, "salience", 0.7);
            var tags = extractJsonStringArray(json, "tags");
            var driveWeights = extractDriveWeights(json);

            if (label.isBlank()) return heuristicTheme(fragments, now);

            return new DerivedTheme(
                    UUID.randomUUID().toString(), now, now,
                    tags.isEmpty() ? List.of("dialogue") : tags,
                    label, Math.clamp(salience, 0.0, 1.0), driveWeights,
                    fragments.stream()
                            .filter(f -> f instanceof IndividualEpisode)
                            .map(NarrativeFragment::id).toList());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "LLM theme derivation failed, using heuristic", e);
            return heuristicTheme(fragments, now);
        }
    }

    private static IndividualEpisode heuristicEpisode(
            List<ConversationRunner.Turn> turns, Instant now) {
        var description = turns.get(0).speakerName() + " and "
                + turns.get(1).speakerName() + " discussed: "
                + truncate(turns.get(0).dialogue(), 80) + "...";
        return new IndividualEpisode(UUID.randomUUID().toString(), now, now,
                List.of("exchange", "turn-" + turns.get(1).number()),
                description, 0.5, List.of());
    }

    private static DerivedTheme heuristicTheme(
            List<NarrativeFragment> fragments, Instant now) {
        return new DerivedTheme(UUID.randomUUID().toString(), now, now,
                List.of("dialogue", "connection"),
                "shared-intellectual-curiosity", 0.7,
                Map.of(DriveAxis.CURIOSITY, 0.3, DriveAxis.AFFILIATION, 0.2),
                fragments.stream()
                        .filter(f -> f instanceof IndividualEpisode)
                        .map(NarrativeFragment::id).toList());
    }

    private @Nullable String invokeAndExtractJson(AgentSessionConfig config) {
        var sb = new StringBuilder();
        agentProvider.invoke(config)
                .subscribe().asStream()
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .forEach(sb::append);
        var raw = sb.toString();
        var start = raw.indexOf('{');
        var end = raw.lastIndexOf('}');
        return (start >= 0 && end > start) ? raw.substring(start, end + 1) : null;
    }

    private static String extractJsonString(String json, String key) {
        var pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        var matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static double extractJsonDouble(String json, String key,
                                             double fallback) {
        var pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*(-?\\d+\\.?\\d*)");
        var matcher = pattern.matcher(json);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : fallback;
    }

    private static List<String> extractJsonStringArray(String json, String key) {
        var pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*\\[([^\\]]*)]");
        var matcher = pattern.matcher(json);
        if (!matcher.find()) return List.of();
        var items = new ArrayList<String>();
        var itemPattern = java.util.regex.Pattern.compile("\"([^\"]+)\"");
        var itemMatcher = itemPattern.matcher(matcher.group(1));
        while (itemMatcher.find()) items.add(itemMatcher.group(1));
        return items;
    }

    private static Map<DriveAxis, Double> extractDriveWeights(String json) {
        var drivesPattern = java.util.regex.Pattern.compile(
                "\"drives\"\\s*:\\s*\\{([^}]*)}");
        var matcher = drivesPattern.matcher(json);
        if (!matcher.find()) return Map.of();
        var drivesJson = matcher.group(1);
        var weights = new java.util.EnumMap<DriveAxis, Double>(DriveAxis.class);
        for (var axis : DriveAxis.values()) {
            var axisPattern = java.util.regex.Pattern.compile(
                    "\"" + axis.name() + "\"\\s*:\\s*(-?\\d+\\.?\\d*)");
            var axisMatcher = axisPattern.matcher(drivesJson);
            if (axisMatcher.find()) {
                weights.put(axis, Math.clamp(
                        Double.parseDouble(axisMatcher.group(1)), -1.0, 1.0));
            }
        }
        return weights;
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    public MoodOrchestrator mood() { return core.mood(); }
    public DriveOrchestrator drives() { return core.drives(); }
    public NarrativeOrchestrator narrative() { return core.narrative(); }
    public @Nullable UserModelOrchestrator userModel() { return core.userModel(); }
    public @Nullable MentalModelOrchestrator mentalModel() { return core.mentalModel(); }
}
