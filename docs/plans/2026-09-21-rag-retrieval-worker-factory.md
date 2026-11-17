# RagRetrievalWorkerFactory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #293 — feat: generic RagRetrievalWorkerFactory
**Issue group:** #293

**Goal:** Provide a reusable static factory that produces a configured
`Worker` for RAG retrieval, eliminating per-domain boilerplate.

**Architecture:** Static factory in `io.casehub.blocks.rag` taking
`CaseContextRetriever`, `QueryExtractionStrategy`, `List<CorpusRef>`,
worker metadata, and optional `maxResults`. Returns a `Worker` whose
function extracts a query, retrieves chunks, maps them to `Map<String,
Object>`, and returns a `WorkerResult` with chunks and summary.

**Tech Stack:** Java 17, JUnit 5, Mockito, neocortex `rag-api`
(provided scope)

## Global Constraints

- `casehub-neocortex-rag-api` must be `<scope>provided</scope>` — consumers declare it themselves
- No CDI — the factory is a stateless utility class
- No Quarkus runtime in tests — plain JUnit 5 + Mockito
- Source in `blocks-core` module, tests in `blocks` module

---

## Batch 1: RagRetrievalWorkerFactory

### Task 1: Add rag-api dependency and implement factory with tests

**Files:**
- Modify: `blocks-core/pom.xml:83-88` (add rag-api provided dependency after neocortex-memory-api)
- Create: `blocks-core/src/main/java/io/casehub/blocks/rag/RagRetrievalWorkerFactory.java`
- Create: `blocks/src/test/java/io/casehub/blocks/rag/RagRetrievalWorkerFactoryTest.java`

**Interfaces:**
- Consumes: `io.casehub.neocortex.rag.CaseContextRetriever` (provided), `io.casehub.neocortex.rag.QueryExtractionStrategy` (provided), `io.casehub.neocortex.rag.CorpusRef`, `io.casehub.neocortex.rag.RetrievedChunk`, `io.casehub.neocortex.rag.RetrievalQuery`, `io.casehub.worker.api.Worker`, `io.casehub.worker.api.WorkerResult`
- Produces: `RagRetrievalWorkerFactory.create(String name, String capabilityName, CaseContextRetriever retriever, QueryExtractionStrategy strategy, List<CorpusRef> corpora, int maxResults) → Worker`, `RagRetrievalWorkerFactory.create(String name, String capabilityName, CaseContextRetriever retriever, QueryExtractionStrategy strategy, List<CorpusRef> corpora) → Worker` (default maxResults=10)

- [ ] **Step 1: Add `casehub-neocortex-rag-api` dependency to `blocks-core/pom.xml`**

Add after the existing `casehub-neocortex-memory-api` dependency block (line 88):

```xml
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-neocortex-rag-api</artifactId>
            <version>${project.version}</version>
            <scope>provided</scope>
        </dependency>
```

- [ ] **Step 2: Verify the project compiles with the new dependency**

Run: `mvn --batch-mode compile -pl blocks-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Write failing tests**

Create `blocks/src/test/java/io/casehub/blocks/rag/RagRetrievalWorkerFactoryTest.java`:

```java
package io.casehub.blocks.rag;

import io.casehub.neocortex.rag.CaseContextRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.QueryExtractionStrategy;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagRetrievalWorkerFactoryTest {

    private final CaseContextRetriever retriever = mock(CaseContextRetriever.class);

    @Test
    void happyPathReturnsChunksAndSummary() {
        var chunk = new RetrievedChunk("threat intel content", "doc-1", 0.87,
                Map.of("source", "attck"));
        var corpora = List.of(new CorpusRef("tenant-1", "corpus-1"));
        QueryExtractionStrategy strategy = ctx -> RetrievalQuery.of("test query");

        when(retriever.retrieve(any(Map.class), any(QueryExtractionStrategy.class),
                eq(corpora), eq(5)))
                .thenReturn(List.of(chunk));

        Worker worker = RagRetrievalWorkerFactory.create(
                "rag-worker", "rag-retrieval", retriever, strategy, corpora, 5);

        @SuppressWarnings("unchecked")
        var result = (WorkerResult<Map<String, Object>>) worker.function()
                .apply(Map.of("alert", "suspicious activity"), null);

        assertThat(result.output()).containsKey("retrievedChunks");
        assertThat(result.output()).containsEntry("summary", "1 relevant chunk(s) retrieved");

        @SuppressWarnings("unchecked")
        var chunks = (List<Map<String, Object>>) result.output().get("retrievedChunks");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).containsEntry("content", "threat intel content");
        assertThat(chunks.get(0)).containsEntry("sourceDocumentId", "doc-1");
        assertThat(chunks.get(0)).containsEntry("relevanceScore", 0.87);
    }

    @Test
    void emptyResultsReturnNoDocumentsSummary() {
        var corpora = List.of(new CorpusRef("tenant-1", "corpus-1"));
        QueryExtractionStrategy strategy = ctx -> RetrievalQuery.of("no match query");

        when(retriever.retrieve(any(Map.class), any(QueryExtractionStrategy.class),
                eq(corpora), eq(10)))
                .thenReturn(List.of());

        Worker worker = RagRetrievalWorkerFactory.create(
                "rag-worker", "rag-retrieval", retriever, strategy, corpora, 10);

        @SuppressWarnings("unchecked")
        var result = (WorkerResult<Map<String, Object>>) worker.function()
                .apply(Map.of(), null);

        assertThat(result.output()).containsEntry("summary", "No relevant documents found");

        @SuppressWarnings("unchecked")
        var chunks = (List<Map<String, Object>>) result.output().get("retrievedChunks");
        assertThat(chunks).isEmpty();
    }

    @Test
    void defaultMaxResultsIsTen() {
        var corpora = List.of(new CorpusRef("tenant-1", "corpus-1"));
        QueryExtractionStrategy strategy = ctx -> RetrievalQuery.of("query");

        when(retriever.retrieve(any(Map.class), any(QueryExtractionStrategy.class),
                eq(corpora), eq(10)))
                .thenReturn(List.of());

        Worker worker = RagRetrievalWorkerFactory.create(
                "rag-worker", "rag-retrieval", retriever, strategy, corpora);

        worker.function().apply(Map.of(), null);

        verify(retriever).retrieve(any(Map.class), any(QueryExtractionStrategy.class),
                eq(corpora), eq(10));
    }

    @Test
    void workerMetadataIsSet() {
        var corpora = List.of(new CorpusRef("tenant-1", "corpus-1"));
        QueryExtractionStrategy strategy = ctx -> RetrievalQuery.of("query");

        Worker worker = RagRetrievalWorkerFactory.create(
                "my-rag-worker", "my-rag-cap", retriever, strategy, corpora);

        assertThat(worker.name()).isEqualTo("my-rag-worker");
        assertThat(worker.capabilities()).containsExactly("my-rag-cap");
    }

    @Test
    void nullQueryFromStrategyReturnsEmpty() {
        var corpora = List.of(new CorpusRef("tenant-1", "corpus-1"));
        QueryExtractionStrategy strategy = ctx -> null;

        when(retriever.retrieve(any(Map.class), any(QueryExtractionStrategy.class),
                eq(corpora), eq(10)))
                .thenReturn(List.of());

        Worker worker = RagRetrievalWorkerFactory.create(
                "rag-worker", "rag-retrieval", retriever, strategy, corpora);

        @SuppressWarnings("unchecked")
        var result = (WorkerResult<Map<String, Object>>) worker.function()
                .apply(Map.of(), null);

        assertThat(result.output()).containsEntry("summary", "No relevant documents found");
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest=RagRetrievalWorkerFactoryTest -q`
Expected: FAIL — `RagRetrievalWorkerFactory` does not exist

- [ ] **Step 5: Implement `RagRetrievalWorkerFactory`**

Create `blocks-core/src/main/java/io/casehub/blocks/rag/RagRetrievalWorkerFactory.java`:

```java
package io.casehub.blocks.rag;

import io.casehub.neocortex.rag.CaseContextRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.QueryExtractionStrategy;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;

import java.util.List;
import java.util.Map;

public final class RagRetrievalWorkerFactory {

    private static final int DEFAULT_MAX_RESULTS = 10;

    private RagRetrievalWorkerFactory() {}

    public static Worker create(String name, String capabilityName,
                                CaseContextRetriever retriever,
                                QueryExtractionStrategy strategy,
                                List<CorpusRef> corpora, int maxResults) {
        return Worker.builder()
                .name(name)
                .capabilityName(capabilityName)
                .function((Map<String, Object> input) -> {
                    List<RetrievedChunk> chunks =
                            retriever.retrieve(input, strategy, corpora, maxResults);
                    String summary = chunks.isEmpty()
                            ? "No relevant documents found"
                            : chunks.size() + " relevant chunk(s) retrieved";
                    return WorkerResult.of(Map.of(
                            "retrievedChunks", chunks.stream()
                                    .map(CaseContextRetriever::toMap).toList(),
                            "summary", summary));
                })
                .build();
    }

    public static Worker create(String name, String capabilityName,
                                CaseContextRetriever retriever,
                                QueryExtractionStrategy strategy,
                                List<CorpusRef> corpora) {
        return create(name, capabilityName, retriever, strategy,
                corpora, DEFAULT_MAX_RESULTS);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest=RagRetrievalWorkerFactoryTest -q`
Expected: PASS — all 5 tests green

- [ ] **Step 7: Run full test suite**

Run: `mvn --batch-mode test -q`
Expected: BUILD SUCCESS — no regressions

- [ ] **Step 8: Commit**

```bash
git add blocks-core/pom.xml \
        blocks-core/src/main/java/io/casehub/blocks/rag/RagRetrievalWorkerFactory.java \
        blocks/src/test/java/io/casehub/blocks/rag/RagRetrievalWorkerFactoryTest.java
git commit -m "feat(#293): add RagRetrievalWorkerFactory

Static factory producing a Worker that extracts a query via
QueryExtractionStrategy, retrieves chunks from CaseContextRetriever,
and returns mapped results with a summary. Eliminates per-domain
RAG retrieval worker boilerplate.

Refs #293

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## References

- [2026-09-21-rag-retrieval-worker-factory-design.md] — design spec this plan implements
- casehubio/soc `RuleRagRetrievalWorker.java` — reference implementation
- casehubio/neocortex `rag-api` — QueryExtractionStrategy, CaseContextRetriever, CorpusRef
- casehubio/worker `api` — Worker, WorkerResult
- blocks-core/pom.xml:83-88 — existing neocortex-memory-api provided dependency (pattern match)
- casehubio/blocks#293 — focal issue
