# STT Accuracy Pipeline — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #212 — STT accuracy pipeline
**Issue group:** #212

**Goal:** Replace Zipformer streaming STT with whisper.cpp (via FFM) and add a multi-stage correction pipeline (SymSpell + phonetic matching + n-gram ranking) for accurate, context-aware transcription.

**Architecture:** whisper.cpp provides pseudo-streaming Whisper with `initial_prompt` vocabulary biasing via FFM/Panama. TranscriptCorrector composes pluggable CorrectionStrategy instances (SymSpell edit-distance + Double Metaphone phonetic) ranked by bigram n-gram context. Runs as a separate stage before the existing CleanupConfig filter chain.

**Tech Stack:** Java 22 (FFM/Panama), whisper.cpp C API, SymSpell algorithm, Double Metaphone, bigram language model, JUnit 5, Mockito, AssertJ

## Global Constraints

- Java 22 for FFM/Panama (speech-sherpa module), Java 17 for speech-api/speech-ws
- No new Maven dependencies for correction engine (pure Java)
- whisper.cpp native library auto-provisioned via Provisioner (same pattern as sherpa-onnx)
- All data files (dictionary, bigrams, GGML models) auto-downloaded with SHA-256 verification
- CorrectionStrategy SPI in speech-api; implementations in speech-sherpa
- TranscriptCorrector is NOT a TextFilter — separate stage, session-scoped

---

## Batch 1: Correction Engine

After this batch: SymSpell + phonetic + n-gram correction works standalone. No native deps. "relimberate" → "limerick" in context.

### Task 1: CorrectionStrategy SPI + TranscriptCorrector

**Files:**
- Create: `speech-api/src/main/java/io/casehub/blocks/speech/CorrectionStrategy.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/correction/TranscriptCorrector.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/correction/TranscriptCorrectorTest.java`

**Interfaces:**
- Produces: `CorrectionStrategy` (SPI — consumed by Task 2, 3, and future strategies), `TranscriptCorrector.correct(String)`, `TranscriptCorrector.addVocabulary(String...)`

- [ ] **Step 1: Write CorrectionStrategy SPI**

```java
package io.casehub.blocks.speech;

import java.util.List;

@FunctionalInterface
public interface CorrectionStrategy {
    List<Candidate> candidates(String word, CorrectionContext context);

    record Candidate(String word, double confidence, String source) {}
    record CorrectionContext(String previousWord, String nextWord, java.util.Set<String> conversationVocabulary) {}
}
```

- [ ] **Step 2: Write failing test for TranscriptCorrector**

```java
@Test
void correctsUnknownWordUsingStrategy() {
    var strategy = mock(CorrectionStrategy.class);
    when(strategy.candidates(eq("relimberate"), any())).thenReturn(
        List.of(new CorrectionStrategy.Candidate("limerick", 0.8, "test")));

    var corrector = new TranscriptCorrector(List.of(strategy), null, Set.of("limerick", "read", "a"));
    assertThat(corrector.correct("read a relimberate")).isEqualTo("read a limerick");
}

@Test
void leavesKnownWordsUnchanged() {
    var corrector = new TranscriptCorrector(List.of(), null, Set.of("hello", "world"));
    assertThat(corrector.correct("hello world")).isEqualTo("hello world");
}

@Test
void addVocabularyExpandsDictionary() {
    var corrector = new TranscriptCorrector(List.of(), null, Set.of("hello"));
    corrector.addVocabulary("limerick");
    assertThat(corrector.correct("limerick")).isEqualTo("limerick");
}
```

- [ ] **Step 3: Run tests — verify they fail**

Run: `mvn -pl speech-sherpa test -Dtest=TranscriptCorrectorTest`

- [ ] **Step 4: Implement TranscriptCorrector**

```java
package io.casehub.blocks.speech.sherpa.correction;

import io.casehub.blocks.speech.CorrectionStrategy;
import io.casehub.blocks.speech.CorrectionStrategy.Candidate;
import io.casehub.blocks.speech.CorrectionStrategy.CorrectionContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TranscriptCorrector {
    private final List<CorrectionStrategy> strategies;
    private final @org.jspecify.annotations.Nullable NgramModel ngramModel;
    private final Set<String> dictionary;

    public TranscriptCorrector(List<CorrectionStrategy> strategies,
                               @org.jspecify.annotations.Nullable NgramModel ngramModel,
                               Set<String> dictionary) {
        this.strategies = List.copyOf(strategies);
        this.ngramModel = ngramModel;
        this.dictionary = ConcurrentHashMap.newKeySet();
        this.dictionary.addAll(dictionary);
    }

    public String correct(String text) {
        String[] words = text.split("\\s+");
        var result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(' ');
            String word = words[i];
            if (dictionary.contains(word.toLowerCase())) {
                result.append(word);
                continue;
            }
            String prev = i > 0 ? words[i - 1].toLowerCase() : "";
            String next = i + 1 < words.length ? words[i + 1].toLowerCase() : "";
            var ctx = new CorrectionContext(prev, next, Collections.unmodifiableSet(dictionary));
            var allCandidates = new ArrayList<Candidate>();
            for (var strategy : strategies) {
                try {
                    allCandidates.addAll(strategy.candidates(word.toLowerCase(), ctx));
                } catch (Exception e) { /* graceful degradation */ }
            }
            if (allCandidates.isEmpty()) {
                result.append(word);
                continue;
            }
            String best = rankAndSelect(allCandidates, prev, next);
            result.append(best != null ? best : word);
        }
        return result.toString();
    }

    public void addVocabulary(String... words) {
        for (String w : words) dictionary.add(w.toLowerCase());
    }

    private String rankAndSelect(List<Candidate> candidates, String prev, String next) {
        if (ngramModel == null) {
            return candidates.stream()
                .max(Comparator.comparingDouble(Candidate::confidence))
                .map(Candidate::word).orElse(null);
        }
        return candidates.stream()
            .max(Comparator.comparingDouble(c -> ngramModel.score(prev, c.word(), next)))
            .map(Candidate::word).orElse(null);
    }
}
```

- [ ] **Step 5: Run tests — verify they pass**

- [ ] **Step 6: Commit**

```
feat(#212): add CorrectionStrategy SPI and TranscriptCorrector compositor
```

---

### Task 2: SymSpellIndex + SymSpellStrategy

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/correction/SymSpellIndex.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/correction/SymSpellStrategy.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/correction/SymSpellIndexTest.java`
- Create: `speech-sherpa/src/test/resources/test-dictionary.txt` (small test fixture)

**Interfaces:**
- Consumes: `CorrectionStrategy` (from Task 1)
- Produces: `SymSpellIndex.lookup(String word) → List<SuggestItem>`, `SymSpellStrategy implements CorrectionStrategy`

- [ ] **Step 1: Create test dictionary fixture**

```
# test-dictionary.txt (word<tab>frequency)
the	23135851162
of	13151942776
and	12997637966
to	12136980858
a	9081174698
in	8469404971
for	5933321709
is	4705743816
on	3750423199
that	3400031103
hello	1000000
world	900000
limerick	50000
cat	80000
```

- [ ] **Step 2: Write failing tests**

```java
@Test
void loadsFromDictionaryAndFindsEditDistance1() {
    var index = SymSpellIndex.fromResource("test-dictionary.txt");
    var results = index.lookup("helo");
    assertThat(results).extracting(SymSpellIndex.SuggestItem::word).contains("hello");
}

@Test
void respectsMaxEditDistance() {
    var index = SymSpellIndex.fromResource("test-dictionary.txt");
    var results = index.lookup("xyzabc");
    assertThat(results).isEmpty();
}

@Test
void dynamicAddMakesWordKnown() {
    var index = SymSpellIndex.fromResource("test-dictionary.txt");
    index.add("avatar", 10000);
    var results = index.lookup("avtar");
    assertThat(results).extracting(SymSpellIndex.SuggestItem::word).contains("avatar");
}

@Test
void strategyReturnsCandidates() {
    var index = SymSpellIndex.fromResource("test-dictionary.txt");
    var strategy = new SymSpellStrategy(index);
    var ctx = new CorrectionStrategy.CorrectionContext("", "", Set.of());
    var candidates = strategy.candidates("helo", ctx);
    assertThat(candidates).isNotEmpty();
    assertThat(candidates).extracting(CorrectionStrategy.Candidate::word).contains("hello");
}
```

- [ ] **Step 3: Run tests — verify they fail**

- [ ] **Step 4: Implement SymSpellIndex**

Pure Java Symmetric Delete algorithm:
- Constructor loads word frequency file, pre-computes delete dictionary (all deletes within max_edit_distance=2)
- `lookup(String)` generates deletes for the input, intersects with the delete dictionary, computes actual edit distances
- `SuggestItem` record: `(String word, int distance, long frequency)`
- `add(String, long)` for dynamic vocabulary — generates delete entries for the new word
- `fromResource(String)` factory loads from classpath resource
- `fromFile(Path)` factory loads from filesystem (for provisioned data)

- [ ] **Step 5: Implement SymSpellStrategy**

```java
public final class SymSpellStrategy implements CorrectionStrategy {
    private final SymSpellIndex index;

    public SymSpellStrategy(SymSpellIndex index) { this.index = index; }

    @Override
    public List<Candidate> candidates(String word, CorrectionContext context) {
        return index.lookup(word).stream()
            .map(s -> new Candidate(s.word(),
                1.0 - (s.distance() / 3.0),  // confidence inversely proportional to distance
                "symspell"))
            .toList();
    }
}
```

- [ ] **Step 6: Run tests — verify they pass**

- [ ] **Step 7: Commit**

```
feat(#212): add SymSpellIndex — Symmetric Delete edit-distance lookup
```

---

### Task 3: PhoneticIndex + PhoneticStrategy

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/correction/DoubleMetaphone.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/correction/PhoneticIndex.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/correction/PhoneticStrategy.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/correction/PhoneticIndexTest.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/correction/DoubleMetaphoneTest.java`

**Interfaces:**
- Consumes: `CorrectionStrategy` (from Task 1), dictionary word list (shared with SymSpellIndex)
- Produces: `PhoneticIndex.lookup(String) → List<String>`, `PhoneticStrategy implements CorrectionStrategy`, `DoubleMetaphone.compute(String) → String[]`

- [ ] **Step 1: Write failing tests for DoubleMetaphone**

```java
@Test
void limericksSharePhoneticCode() {
    var dm = new DoubleMetaphone();
    String[] code1 = dm.compute("limerick");
    String[] code2 = dm.compute("relimberate");
    // At least one code should overlap (primary or alternate)
    assertThat(Set.of(code1)).containsAnyElementsOf(Set.of(code2));
}

@Test
void commonWordsProduceStableCodes() {
    var dm = new DoubleMetaphone();
    assertThat(dm.compute("hello")[0]).isEqualTo(dm.compute("hello")[0]);
}
```

- [ ] **Step 2: Implement DoubleMetaphone**

Port the standard Double Metaphone algorithm to Java. Pure algorithmic — ~300 lines of character-based rules. No dependencies. Well-documented open algorithm with reference implementations in many languages.

Key method: `String[] compute(String word)` — returns `[primaryCode, alternateCode]`. Both codes are 4-character strings.

- [ ] **Step 3: Write failing tests for PhoneticIndex**

```java
@Test
void findsSoundAlikeCandidates() {
    var index = PhoneticIndex.fromWords(List.of("limerick", "hello", "world", "cat"));
    var candidates = index.lookup("relimberate");
    assertThat(candidates).contains("limerick");
}

@Test
void strategyReturnsCandidates() {
    var index = PhoneticIndex.fromWords(List.of("limerick", "hello"));
    var strategy = new PhoneticStrategy(index);
    var ctx = new CorrectionStrategy.CorrectionContext("read", "please", Set.of());
    var candidates = strategy.candidates("relimberate", ctx);
    assertThat(candidates).extracting(CorrectionStrategy.Candidate::word).contains("limerick");
}
```

- [ ] **Step 4: Implement PhoneticIndex + PhoneticStrategy**

PhoneticIndex:
- Constructor takes a word list, computes Metaphone codes, builds `Map<String, List<String>>` (code → words)
- `lookup(String)` computes input's codes, returns all words matching either code
- `addWord(String)` for dynamic vocabulary
- `fromWords(List<String>)` factory

PhoneticStrategy:
- Wraps PhoneticIndex, returns Candidates with `source: "phonetic"` and confidence 0.6 (lower than SymSpell — phonetic matches are less precise)

- [ ] **Step 5: Run all tests — verify they pass**

- [ ] **Step 6: Commit**

```
feat(#212): add PhoneticIndex — Double Metaphone sound-alike matching
```

---

### Task 4: NgramModel + Correction Integration Test

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/correction/NgramModel.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/correction/NgramModelTest.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/correction/CorrectionIntegrationTest.java`
- Create: `speech-sherpa/src/test/resources/test-bigrams.txt` (small fixture)

**Interfaces:**
- Consumes: none (standalone)
- Produces: `NgramModel.score(String prev, String candidate, String next) → double`

- [ ] **Step 1: Create test bigram fixture**

```
# test-bigrams.txt (word1<tab>word2<tab>count)
a	limerick	5000
a	cat	8000
read	a	12000
the	cat	9000
hello	world	6000
```

- [ ] **Step 2: Write failing tests for NgramModel**

```java
@Test
void ranksLimerickHigherThanRelimberateInContext() {
    var model = NgramModel.fromResource("test-bigrams.txt");
    double scoreLimerick = model.score("a", "limerick", "");
    double scoreUnknown = model.score("a", "relimberate", "");
    assertThat(scoreLimerick).isGreaterThan(scoreUnknown);
}

@Test
void unknownBigramFallsBackToUnigram() {
    var model = NgramModel.fromResource("test-bigrams.txt");
    double score = model.score("xyz", "hello", "abc");
    assertThat(score).isGreaterThan(Double.NEGATIVE_INFINITY);
}
```

- [ ] **Step 3: Implement NgramModel**

- Load bigram frequencies from tab-separated file
- Internal storage: `HashMap<Long, Float>` keyed by `hash(word1) * 31 + hash(word2)` — simple, adequate for ~1M bigrams. If memory is a concern, swap for Eclipse Collections `LongFloatHashMap` later.
- `score()`: log-probability of candidate in context. `log(P(prev, candidate)) + log(P(candidate, next))`. Unknown bigrams fall back to unigram smoothing: `log(freq(candidate) / totalFreq) - penalty`.
- `fromResource(String)` and `fromFile(Path)` factories.

- [ ] **Step 4: Write integration test — full correction pipeline**

```java
@Test
void correctsRelimberateToLimerickInContext() {
    var symSpell = SymSpellIndex.fromResource("test-dictionary.txt");
    var phonetic = PhoneticIndex.fromWords(List.of("limerick", "hello", "world", "cat", "the", "a", "read"));
    var ngram = NgramModel.fromResource("test-bigrams.txt");

    var corrector = new TranscriptCorrector(
        List.of(new SymSpellStrategy(symSpell), new PhoneticStrategy(phonetic)),
        ngram,
        Set.of("the", "a", "read", "hello", "world", "cat", "limerick"));

    assertThat(corrector.correct("read a relimberate")).isEqualTo("read a limerick");
}
```

- [ ] **Step 5: Run all tests — verify they pass**

- [ ] **Step 6: Commit**

```
feat(#212): add NgramModel + end-to-end correction integration test
```

---

## Batch 2: Extended Cleanup

After this batch: filler removal catches discourse markers, GECToR and punctuation are wired.

### Task 5: Extended FillerRemovalFilter

**Files:**
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/FillerRemovalFilter.java`
- Modify: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/FillerRemovalFilterTest.java` (or create if doesn't exist)

**Interfaces:**
- Produces: extended `FillerRemovalFilter.apply()` handling discourse markers, false starts, repeated words

- [ ] **Step 1: Write failing tests for new patterns**

```java
@Test
void removesDiscourseMarkerLikeAfterVerb() {
    assertThat(filter.apply("I was like walking")).isEqualTo("I was walking");
}

@Test
void preservesLikeAsVerb() {
    assertThat(filter.apply("I like cats")).isEqualTo("I like cats");
}

@Test
void removesRepeatedWords() {
    assertThat(filter.apply("I I went to the store")).isEqualTo("I went to the store");
}

@Test
void removesYouKnow() {
    assertThat(filter.apply("it was you know really good")).isEqualTo("it was really good");
}

@Test
void removesIMean() {
    assertThat(filter.apply("I mean the thing is")).isEqualTo("the thing is");
}
```

- [ ] **Step 2: Run tests — verify they fail**

- [ ] **Step 3: Extend FillerRemovalFilter**

Add patterns for:
- `\byou know\b` → remove
- `\bI mean\b` at start → remove
- `\bbasically\b` → remove (when standalone filler)
- `\bwas like\b` → `"was"` (remove "like" after "was")
- Repeated adjacent words: `\b(\w+)\s+\1\b` → `$1`
- Keep destructiveness at 1 for the basic fillers; the discourse marker removal is a separate set of patterns that could be gated by a higher destructiveness if needed

- [ ] **Step 4: Run tests — verify they pass**

- [ ] **Step 5: Commit**

```
feat(#212): extend filler removal — discourse markers, false starts
```

---

### Task 6: Fix GECToR Provisioning + Wire PunctuationFilter

**Files:**
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java` (fix GECToR URL/checksum)
- Modify: `speech-demo/src/main/java/io/casehub/blocks/speech/demo/SpeechProducers.java` (wire PunctuationFilter)

**Interfaces:**
- Consumes: existing `PunctuationFilter`, `GectorFilter`, `Provisioner`
- Produces: working GECToR download, PunctuationFilter in demo cleanup chain

- [ ] **Step 1: Diagnose GECToR download failure**

Check current URL and SHA in Provisioner against actual GitHub release. The error was: "Failed to download ...gector-deberta-base-5k.tar.bz2. Download manually." — likely a stale URL or missing release.

- [ ] **Step 2: Fix Provisioner URL/checksum for GECToR**

Update `GECTOR_MODEL_EXPECTED_FILES` and download URL to point at the current release. Verify SHA-256 matches.

- [ ] **Step 3: Wire PunctuationFilter into demo CleanupConfig**

```java
// In SpeechProducers.cleanupConfig():
try {
    var gector = GectorFilter.withDefaults();
    var punctDir = Provisioner.ensurePunctuationModel();
    var punct = new PunctuationFilter(punctDir);
    return CleanupConfig.of(
        new FillerRemovalFilter(),
        new CasingFilter(),
        punct,
        gector);
} catch (Exception e) {
    // ... graceful fallback
}
```

- [ ] **Step 4: Test locally — verify GECToR downloads and pipeline runs**

- [ ] **Step 5: Commit**

```
fix(#212): fix GECToR model provisioning, wire PunctuationFilter
```

---

## Batch 3: Whisper FFM

After this batch: whisper.cpp runs via FFM, pseudo-streaming works, models auto-provision.

### Task 7: WhisperLibrary FFM + Provisioner

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/WhisperLibrary.java`
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java` (add whisper model + native lib)
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/WhisperLibraryTest.java`

**Interfaces:**
- Produces: `WhisperLibrary.load()`, `WhisperLibrary.isAvailable()`, downcall handles for `whisper_init_from_file`, `whisper_full`, `whisper_full_n_segments`, `whisper_full_get_segment_text`, `whisper_free`

- [ ] **Step 1: Add whisper provisioning to Provisioner**

Add `ensureWhisperNativeLib()` — downloads `libwhisper` shared library for the current platform. Follow the same pattern as `ensureNativeLibrary()` for sherpa-onnx.

Add `ensureWhisperModel(String modelName)` — downloads GGML model from HuggingFace. Default: `ggml-base.en.bin`.

- [ ] **Step 2: Write failing test for WhisperLibrary**

```java
@Test
@EnabledIf("isWhisperAvailable")
void loadsLibraryAndResolvesSymbols() {
    var lib = WhisperLibrary.load();
    assertThat(lib).isNotNull();
    assertThat(lib.whisperInit).isNotNull();
    assertThat(lib.whisperFull).isNotNull();
    assertThat(lib.whisperFree).isNotNull();
}

static boolean isWhisperAvailable() {
    return WhisperLibrary.isAvailable();
}
```

- [ ] **Step 3: Implement WhisperLibrary**

Follow `SherpaLibrary` pattern exactly:
- Singleton with `load()` / `load(Path)` / `isAvailable()`
- `SymbolLookup.libraryLookup()` for the native lib
- ~10 downcall `MethodHandle` fields for the whisper.cpp C API functions
- `downcall()` helper for creating handles from `FunctionDescriptor`

Key handles:
```java
final MethodHandle whisperInit;        // whisper_init_from_file(path) → context*
final MethodHandle whisperFree;        // whisper_free(context*)
final MethodHandle whisperFull;        // whisper_full(context*, params, samples*, n) → int
final MethodHandle whisperFullDefaultParams; // whisper_full_default_params(strategy) → params
final MethodHandle whisperFullNSegments;     // whisper_full_n_segments(context*) → int
final MethodHandle whisperFullGetSegmentText; // whisper_full_get_segment_text(context*, i) → char*
```

- [ ] **Step 4: Run tests — verify they pass (on machines with whisper.cpp)**

- [ ] **Step 5: Commit**

```
feat(#212): add WhisperLibrary — FFM bindings for whisper.cpp C API
```

---

### Task 8: WhisperSpeechToText

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/WhisperSpeechToText.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/WhisperSpeechToTextTest.java`

**Interfaces:**
- Consumes: `WhisperLibrary` (Task 7), `StreamingSpeechToTextService` (speech-api), `TranscriptionOptions` (Task 9 — uses `vocabularyHint` if present, null-safe)
- Produces: `WhisperSpeechToText implements StreamingSpeechToTextService`, `withDefaults()` factory

- [ ] **Step 1: Write failing test**

```java
@Test
@EnabledIf("isWhisperAvailable")
void transcribesKnownAudioFile() throws Exception {
    var stt = WhisperSpeechToText.withDefaults();
    try {
        var stream = stt.startStream(TranscriptionOptions.defaults());
        float[] samples = loadTestAudio(); // known WAV with "hello world"
        stream.acceptSamples(samples, 16000);
        var result = stream.finalResult();
        assertThat(result.text().toLowerCase()).contains("hello");
        stream.close();
    } finally {
        stt.close();
    }
}
```

- [ ] **Step 2: Implement WhisperSpeechToText**

Key design points:
- Shared `whisper_context` — loaded once at construction, shared across streams
- `ReentrantLock` for serialized inference
- `WhisperRecognitionStream` inner class:
  - `float[]` buffer with 30-second cap (480,000 samples at 16kHz)
  - `acceptSamples()` — append to buffer (discard oldest if over cap)
  - `partialResult()` — acquire lock, run `whisper_full()` on buffer, diff vs previous, throttle to 500ms
  - `finalResult()` — acquire lock, run `whisper_full()` with `initial_prompt` from `TranscriptionOptions.vocabularyHint()`, return full text
- `withDefaults()` factory — provisions model, loads library, creates context

- [ ] **Step 3: Run tests — verify they pass**

- [ ] **Step 4: Commit**

```
feat(#212): add WhisperSpeechToText — pseudo-streaming Whisper via FFM
```

---

## Batch 4: Pipeline Assembly

After this batch: full pipeline wired end-to-end in the avatar demo.

### Task 9: TranscriptionOptions Extension + ConversationVocabulary

**Files:**
- Modify: `speech-api/src/main/java/io/casehub/blocks/speech/TranscriptionOptions.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/correction/ConversationVocabulary.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/correction/ConversationVocabularyTest.java`

**Interfaces:**
- Produces: `TranscriptionOptions.withVocabularyHint(String)`, `ConversationVocabulary.addFromText(String)`, `ConversationVocabulary.asPromptHint()`, `ConversationVocabulary.terms()`

- [ ] **Step 1: Extend TranscriptionOptions (backward compatible)**

Add 4th field `vocabularyHint` with backward-compatible 3-arg constructor:

```java
public record TranscriptionOptions(String audioFormat, String languageHint,
                                    String modelSize, @Nullable String vocabularyHint) {
    public TranscriptionOptions(String audioFormat, String languageHint, String modelSize) {
        this(audioFormat, languageHint, modelSize, null);
    }
    public static TranscriptionOptions defaults() {
        return new TranscriptionOptions("wav", null, "base.en", null);
    }
    public TranscriptionOptions withVocabularyHint(String hint) {
        return new TranscriptionOptions(audioFormat, languageHint, modelSize, hint);
    }
}
```

- [ ] **Step 2: Write failing tests for ConversationVocabulary**

```java
@Test
void extractsTermsFromText() {
    var vocab = new ConversationVocabulary();
    vocab.addFromText("There once was a limerick from Nantucket");
    assertThat(vocab.terms()).contains("limerick", "nantucket");
    assertThat(vocab.terms()).doesNotContain("a", "was", "once"); // stop words / short
}

@Test
void promptHintJoinsTermsWithSpaces() {
    var vocab = new ConversationVocabulary();
    vocab.addFromText("Tell me about limericks and sonnets");
    assertThat(vocab.asPromptHint()).contains("limericks").contains("sonnets");
}
```

- [ ] **Step 3: Implement ConversationVocabulary**

- `ConcurrentHashMap.newKeySet()` for thread safety
- `addFromText()` — split on whitespace, lowercase, keep words ≥ 4 chars, exclude stop words (small static set)
- `asPromptHint()` — join all terms with spaces (for Whisper `initial_prompt`)
- `terms()` — unmodifiable view

- [ ] **Step 4: Run tests — verify they pass**

- [ ] **Step 5: Verify existing callers compile with TranscriptionOptions change**

Run: `mvn -pl speech-api,speech-sherpa,speech-ws,speech-demo compile`

- [ ] **Step 6: Commit**

```
feat(#212): extend TranscriptionOptions + add ConversationVocabulary
```

---

### Task 10: SpeechSession + SpeechProducers Integration

**Files:**
- Modify: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/SpeechSession.java`
- Modify: `speech-demo/src/main/java/io/casehub/blocks/speech/demo/SpeechProducers.java`
- Modify: `speech-ws/src/test/java/io/casehub/blocks/speech/ws/SpeechSessionTest.java`

**Interfaces:**
- Consumes: `TranscriptCorrector` (Task 1), `ConversationVocabulary` (Task 9), `WhisperSpeechToText` (Task 8)

- [ ] **Step 1: Add TranscriptCorrector + ConversationVocabulary to SpeechSession**

Add fields and constructor parameter:
```java
private final @Nullable TranscriptCorrector transcriptCorrector;
private final ConversationVocabulary conversationVocabulary = new ConversationVocabulary();
```

In `handleStop()` and `handleText()`, after STT and before cleanup:
```java
String raw = /* STT result */;
String corrected = transcriptCorrector != null ? transcriptCorrector.correct(raw) : raw;
String clean = cleanupConfig.apply(corrected);
```

After LLM response:
```java
conversationVocabulary.addFromText(responseText);
if (transcriptCorrector != null) {
    transcriptCorrector.addVocabulary(conversationVocabulary.terms().toArray(String[]::new));
}
```

- [ ] **Step 2: Wire WhisperSpeechToText in SpeechProducers**

Replace `SherpaOnnxStreamingSpeechToText.withDefaults()` with `WhisperSpeechToText.withDefaults()`:
```java
@Produces @ApplicationScoped
StreamingSpeechToTextService stt() {
    if (WhisperLibrary.isAvailable()) {
        return WhisperSpeechToText.withDefaults();
    }
    return SherpaOnnxStreamingSpeechToText.withDefaults(); // fallback
}
```

Create and inject TranscriptCorrector:
```java
@Produces @ApplicationScoped
TranscriptCorrector corrector() {
    var dictPath = Provisioner.ensureDictionary("frequency_dictionary_en_82_765");
    var symSpell = SymSpellIndex.fromFile(dictPath);
    var phonetic = PhoneticIndex.fromSymSpellIndex(symSpell);
    var ngram = NgramModel.fromFile(Provisioner.ensureBigrams("bigrams_en"));
    return new TranscriptCorrector(
        List.of(new SymSpellStrategy(symSpell), new PhoneticStrategy(phonetic)),
        ngram, symSpell.dictionary());
}
```

- [ ] **Step 3: Update SpeechSessionTest for new constructor**

Add `transcriptCorrector` parameter (nullable) to test setup. Add test verifying correction runs before cleanup.

- [ ] **Step 4: Full integration test — start demo, type text, verify corrected output**

Run: `mvn -pl speech-demo quarkus:dev`
Type "read a relimberate" → verify transcript shows "read a limerick"

- [ ] **Step 5: Commit**

```
feat(#212): wire STT accuracy pipeline — Whisper + correction in SpeechSession
```

---

## Data Provisioning (cross-cutting)

The following data files need provisioning methods added to `Provisioner.java`. Each follows the existing pattern: download URL, SHA-256 checksum, expected file check, `FileLock` concurrency.

| Method | Downloads | Size | Source |
|--------|-----------|------|--------|
| `ensureWhisperNativeLib()` | `libwhisper.*` | ~5MB | github.com/ggerganov/whisper.cpp/releases |
| `ensureWhisperModel(name)` | `ggml-base.en.bin` | ~142MB | huggingface.co/ggerganov/whisper.cpp |
| `ensureDictionary(name)` | `frequency_dictionary_en_82_765.txt` | ~2MB | github.com/wolfgarbe/SymSpell |
| `ensureBigrams(name)` | `bigrams_en.txt` | ~50MB | pre-built from Google Ngrams |

These are wired in Tasks 7, 8, and 10 as needed.

---

## References

- [2026-08-28-stt-accuracy-pipeline-design.md](../specs/issue-212-stt-pipeline/2026-08-28-stt-accuracy-pipeline-design.md) — design spec
- `speech-api/src/main/java/io/casehub/blocks/speech/` — SPI interfaces
- `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/SherpaLibrary.java` — FFM binding pattern
- `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java` — auto-download pattern
- `speech-ws/src/main/java/io/casehub/blocks/speech/ws/SpeechSession.java` — integration point
- [SymSpell algorithm](https://github.com/wolfgarbe/symspell) — Symmetric Delete
- [whisper.cpp C API](https://github.com/ggerganov/whisper.cpp/blob/master/include/whisper.h) — FFM target
- GitHub #212 — parent issue
