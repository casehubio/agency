# GECToR Grammar Correction TextFilter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #179 — GECToR grammar correction TextFilter
**Issue group:** #179 (within epic #178)

**Goal:** Implement a `TextFilter` at destructiveness 3 using GECToR
(Grammarly's sequence tagger) for token-level grammar correction via
ONNX Runtime. Non-autoregressive, 20-50ms per sentence.

**Architecture:** Reuse `OnnxRuntimeLibrary` from #186 for ONNX inference.
Use SentencePiece4J (pure Java Maven dep) for subword tokenization.
`GectorFilter` composes pre-tokenization → SentencePiece encoding →
ORT inference → softmax/threshold → tag application → iterative loop.

**Tech Stack:** Java 22, OnnxRuntimeLibrary (existing), SentencePiece4J,
protobuf-java (existing), Python (export script only)

## Global Constraints

- Module: `speech-sherpa` (`casehub-blocks-speech-sherpa`)
- Java target: 22
- Package: `io.casehub.blocks.speech.sherpa`
- No CDI/Quarkus dependencies
- Tokenization: SentencePiece4J (`io.github.eix128:sentencepiece4j`) — pure Java, no native deps
- DeBERTa max sequence length: 512 subwords (sentence-level splitting)
- Iterative inference: up to `maxIterations` passes (default 5), stops when no corrections made
- Softmax + threshold filtering: `keepConfidence` and `minErrorProb` control correction aggressiveness
- [CLS] prepended, [SEP] appended to every input sequence
- `▁` (U+2581) marks word-INITIAL positions in SentencePiece (not continuations)
- Word-subword alignment: first-subword-wins for tag prediction
- Verb transform fallback: keep original word if not found in dictionary
- ORT env must be shared across sessions (refactor from #186 design debt)
- Export script: Python, lives in `speech-sherpa/scripts/`

---

## Batch 1: Pure Java Foundation — Config + TagApplier + WordTokenizer

After this batch: tag vocabulary parsing, tag application logic, and
word-level pre-tokenization all work. No native deps needed.

### Task 1: GectorConfig — tag vocabulary and model config

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/GectorConfig.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/GectorConfigTest.java`
- Create: `speech-sherpa/src/test/resources/test-gector-labels.txt`
- Modify: `speech-sherpa/pom.xml` (add sentencepiece4j dependency)

**Interfaces:**
- Consumes: nothing (foundation)
- Produces:
  - `GectorConfig.fromModelDir(Path)` — factory
  - `GectorConfig.modelPath()` → `Path`
  - `GectorConfig.spieceModelPath()` → `Path`
  - `GectorConfig.maxIterations()` → `int` (default 5)
  - `GectorConfig.keepConfidence()` → `float` (default 0.0)
  - `GectorConfig.minErrorProb()` → `float` (default 0.0)
  - `GectorConfig.numThreads()` → `int` (default 1)
  - `GectorConfig.provider()` → `String` (default "cpu")
  - `GectorConfig.tagVocabulary()` → `List<String>` (ordered tag labels)
  - `GectorConfig.keepTagId()` → `int` (index of $KEEP)
  - `GectorConfig.verbDictionary()` → `Map<String, Map<String, String>>` (word → form → inflected)

- [ ] **Step 1: Create test labels.txt fixture**

```
$KEEP
$DELETE
$APPEND_the
$APPEND_a
$APPEND_to
$REPLACE_the
$REPLACE_a
$TRANSFORM_VERB_VB_VBZ
$TRANSFORM_VERB_VBZ_VB
$TRANSFORM_CASE_CAPITAL
$TRANSFORM_CASE_LOWER
$TRANSFORM_AGREEMENT_SINGULAR
$TRANSFORM_AGREEMENT_PLURAL
$MERGE_HYPHEN
```

- [ ] **Step 2: Add sentencepiece4j dependency to pom.xml**

```xml
<dependency>
  <groupId>io.github.eix128</groupId>
  <artifactId>sentencepiece4j</artifactId>
  <version>1.0.0</version>
</dependency>
```

- [ ] **Step 3: Write failing tests**

```java
class GectorConfigTest {
    @Test void parsesTagVocabulary() { ... }
    @Test void keepTagId_isZero() { ... }
    @Test void tagCount_matchesFileLines() { ... }
    @Test void defaultMaxIterations() { ... }
    @Test void defaultConfidenceThresholds() { ... }
}
```

- [ ] **Step 4: Implement GectorConfig**

Record with factory `fromModelDir(Path)`. Reads `labels.txt` line by
line into `List<String>`. Finds $KEEP index. Reads `verb-form-vocab.txt`
if present (tab-separated: word → form → inflected).

- [ ] **Step 5: Run tests, verify pass**
- [ ] **Step 6: Commit**

### Task 2: GectorTagApplier — tag application logic

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/GectorTagApplier.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/GectorTagApplierTest.java`

**Interfaces:**
- Consumes: `GectorConfig.tagVocabulary()`, `GectorConfig.verbDictionary()`
- Produces:
  - `GectorTagApplier.apply(List<String> tokens, int[] tagIds, GectorConfig) → List<String>`
  - Returns corrected token list. Returns `true` from `hasChanges()` if any non-$KEEP tag was applied.

- [ ] **Step 1: Write failing tests**

```java
class GectorTagApplierTest {
    @Test void keep_preservesToken() { ... }
    @Test void delete_removesToken() { ... }
    @Test void append_insertsWordAfter() { ... }
    @Test void replace_substitutesWord() { ... }
    @Test void transformCaseCapital() { ... }
    @Test void transformCaseLower() { ... }
    @Test void transformVerbForm() { ... }
    @Test void transformVerbForm_unknownFallsBack() { ... }
    @Test void mergeHyphen() { ... }
    @Test void multipleEdits() { ... }
}
```

- [ ] **Step 2: Implement GectorTagApplier**

Stateless utility. For each (token, tagId) pair, look up tag name from
vocabulary. Parse tag format: `$OPERATION` or `$OPERATION_ARGUMENT`.
Apply the operation. Return new token list + changed flag.

- [ ] **Step 3: Run tests, verify pass**
- [ ] **Step 4: Commit**

### Task 3: WordTokenizer — pre-tokenization with contraction handling

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/WordTokenizer.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/WordTokenizerTest.java`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `WordTokenizer.tokenize(String sentence) → List<String>` — word-level tokens
  - `WordTokenizer.splitSentences(String text) → List<String>` — sentence splitting

- [ ] **Step 1: Write failing tests**

```java
class WordTokenizerTest {
    @Test void simpleWords() { ... } // "hello world" → ["hello", "world"]
    @Test void contraction() { ... } // "don't" → ["do", "n't"]
    @Test void possessive() { ... } // "cat's" → ["cat", "'s"]
    @Test void punctuation() { ... } // "hello," → ["hello", ","]
    @Test void sentenceSplit() { ... } // "Hello. World!" → ["Hello.", "World!"]
    @Test void emptyInput() { ... }
}
```

- [ ] **Step 2: Implement WordTokenizer**

Rule-based tokenizer matching spaCy's English tokenization rules.
Regex-based splitting for contractions (`n't`, `'s`, `'re`, `'ve`,
`'ll`, `'d`, `'m`), punctuation, and whitespace.

- [ ] **Step 3: Run tests, verify pass**
- [ ] **Step 4: Commit**

---

## Batch 2: Model Export + ORT Refactor

After this batch: ONNX model exported and available for testing.
ORT environment shared across sessions.

### Task 4: ORT singleton env refactor

**Files:**
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/OnnxRuntimeLibrary.java`
- Modify: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/OnnxRuntimeLibraryTest.java`
- Verify: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/VitsTextToSpeechTest.java` still passes

**Interfaces:**
- Consumes: existing `OnnxRuntimeLibrary`
- Produces: `createSession(Path, int, String logId)` — reuses singleton env

- [ ] **Step 1: Write test for multiple concurrent sessions**

```java
@Test
void multipleSessions_shareSingletonEnv() {
    var lib = OnnxRuntimeLibrary.load();
    try (var s1 = lib.createSession(testModelPath(), 1);
         var s2 = lib.createSession(testModelPath(), 1)) {
        assertThat(s1.outputCount()).isEqualTo(1);
        assertThat(s2.outputCount()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Refactor createSession to use singleton env**

Move ORT env creation from `createSession()` to `load()` (singleton
init). Session still created per call but reuses the shared env.
Add `logId` parameter (defaults to "casehub").

- [ ] **Step 3: Run full test suite — verify VitsTextToSpeech still passes**
- [ ] **Step 4: Commit**

### Task 5: ONNX export script + model provisioning

**Files:**
- Create: `speech-sherpa/scripts/export_gector_onnx.py`
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java`

**Interfaces:**
- Consumes: gotutiyan/gector PyTorch models (HuggingFace)
- Produces: model.onnx + labels.txt + spiece.model + verb-form-vocab.txt
  in a directory structure matching `GectorConfig.fromModelDir()`

- [ ] **Step 1: Write export script**

Python script: load model from HuggingFace → `torch.onnx.export` with
dynamic axes (batch, sequence length). Export both base and large.
Copy `spiece.model` from tokenizer. Extract `labels.txt` from model
config. Build `verb-form-vocab.txt` from training data.

- [ ] **Step 2: Run export script for base model**
- [ ] **Step 3: Add Provisioner.ensureGectorModel() with checksums**
- [ ] **Step 4: Commit**

---

## Batch 3: Integration — GectorFilter

After this batch: end-to-end grammar correction works as a TextFilter.

### Task 6: GectorFilter — compose everything into TextFilter

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/GectorFilter.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/GectorFilterTest.java`

**Interfaces:**
- Consumes: `GectorConfig`, `GectorTagApplier`, `WordTokenizer`,
  `OnnxRuntimeLibrary`, SentencePiece4J
- Produces:
  - `GectorFilter(GectorConfig, OnnxRuntimeLibrary)` — constructor
  - `apply(String text) → String` — corrected text
  - `name() → "grammar"`
  - `destructiveness() → 3`
  - Implements `TextFilter`, `AutoCloseable`

- [ ] **Step 1: Write failing tests**

```java
@EnabledIf("allDepsAvailable")
class GectorFilterTest {
    @Test void correctsSubjectVerbAgreement() {
        // "He go to school" → "He goes to school"
    }
    @Test void correctsMissingArticle() {
        // "I have cat" → "I have a cat"
    }
    @Test void preservesCorrectText() {
        // "The cat sat on the mat." → unchanged
    }
    @Test void iterativeCorrection() {
        // Input needing 2+ passes
    }
    @Test void name_isGrammar() { ... }
    @Test void destructiveness_is3() { ... }
}
```

- [ ] **Step 2: Implement GectorFilter**

Core `apply()` flow:
1. `WordTokenizer.splitSentences(text)` → sentences
2. For each sentence:
   a. `WordTokenizer.tokenize(sentence)` → word tokens
   b. SentencePiece4J encode each word → subword IDs + alignment map
   c. Prepend [CLS], append [SEP], build attention mask
   d. Create ORT input tensors, run session
   e. Read logits, compute softmax, apply thresholds, argmax
   f. Aggregate subword → word tags (first-subword-wins)
   g. `GectorTagApplier.apply(words, tags)` → corrected words
   h. If changes and iteration < max, repeat from (a)
3. Join sentences

- [ ] **Step 3: Run tests, verify pass**
- [ ] **Step 4: Run full module test suite**
- [ ] **Step 5: Commit**

### Task 7: CLAUDE.md update

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add GectorFilter, GectorConfig, GectorTagApplier, WordTokenizer to class tables**
- [ ] **Step 2: Commit**

---

## References

- [2026-08-26-gector-grammar-filter-design.md](../specs/issue-194-talking-avatar-tier1/2026-08-26-gector-grammar-filter-design.md) — design spec
- [GECToR paper](https://arxiv.org/abs/2005.12592) — architecture and tag vocabulary
- [gotutiyan/gector](https://github.com/gotutiyan/gector) — PyTorch implementation
- `OnnxRuntimeLibrary.java` — existing FFM onnxruntime bindings (reused + refactored)
- `TextFilter.java` — SPI interface
- `PunctuationFilter.java` — existing TextFilter pattern
- [SentencePiece4J](https://github.com/eix128/sentencepiece4j) — pure Java tokenizer
- GitHub #179 — GECToR grammar correction issue
- GitHub #178 — Clean Live Transcription Pipeline epic
