package io.casehub.agency.orchestration.multiswarm;

import io.casehub.eidos.api.DispositionAxis;
import io.casehub.eidos.api.MatchDegree;
import io.casehub.eidos.api.VocabularyInfo;
import io.casehub.eidos.api.VocabularyRegistry;
import io.casehub.eidos.api.VocabularyTerm;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Minimal stub for testing vocabulary-aware consistency checking.
 * Only {@link #resolve} and {@link #subsumes} are implemented.
 */
class StubVocabularyRegistry implements VocabularyRegistry {

    private final String vocabUri;
    private final Map<String, String> termMap;
    private final Set<String> subsumptions;

    StubVocabularyRegistry(String vocabUri, Map<String, String> termMap, Set<String> subsumptions) {
        this.vocabUri = vocabUri;
        this.termMap = termMap;
        this.subsumptions = subsumptions;
    }

    @Override
    public Optional<? extends VocabularyTerm> resolve(String vocabUri, String value) {
        if (!this.vocabUri.equals(vocabUri)) return Optional.empty();
        var canonical = termMap.get(value);
        if (canonical == null) return Optional.empty();
        return Optional.of(new StubTerm(canonical));
    }

    @Override
    public boolean subsumes(String vocabUri, String generalValue, String specificValue) {
        if (!this.vocabUri.equals(vocabUri)) return false;
        return subsumptions.contains(generalValue + "->" + specificValue);
    }

    @Override public <T extends Enum<T> & VocabularyTerm> void register(Class<T> vocab) {}
    @Override public void register(String uri, String name, String version, String description, List<? extends VocabularyTerm> terms) {}
    @Override public boolean isRegistered(String vocabUri) { return this.vocabUri.equals(vocabUri); }
    @Override public Set<String> registeredUris() { return Set.of(vocabUri); }
    @Override public List<? extends VocabularyTerm> allTerms(String vocabUri) { return List.of(); }
    @Override public Optional<String> equivalentValues(String fromUri, String value, String toUri) { return Optional.empty(); }
    @Override public Optional<String> equivalentValues(String fromUri, String value, String toUri, DispositionAxis axis) { return Optional.empty(); }
    @Override public <T extends Enum<T> & VocabularyTerm> Optional<T> resolve(Class<T> vocab, String value) { return Optional.empty(); }
    @Override public <S extends Enum<S> & VocabularyTerm, T extends Enum<T> & VocabularyTerm> Optional<T> equivalentValues(S from, Class<T> targetVocab) { return Optional.empty(); }
    @Override public <S extends Enum<S> & VocabularyTerm, T extends Enum<T> & VocabularyTerm> Optional<T> equivalentValues(S from, Class<T> targetVocab, DispositionAxis axis) { return Optional.empty(); }
    @Override public Optional<VocabularyInfo> vocabularyMetadata(String uri) { return Optional.empty(); }
    @Override public MatchDegree match(String vocabUri, String declaredValue, String requestedValue) { return new MatchDegree.None(); }
    @Override public List<? extends VocabularyTerm> ancestors(String vocabUri, String value) { return List.of(); }
    @Override public List<? extends VocabularyTerm> descendants(String vocabUri, String value) { return List.of(); }
    @Override public Map<String, Set<String>> expandForMatchingByVocabulary(String value) { return Map.of(); }

    private record StubTerm(String value) implements VocabularyTerm {
        @Override public String label() { return value; }
    }
}
