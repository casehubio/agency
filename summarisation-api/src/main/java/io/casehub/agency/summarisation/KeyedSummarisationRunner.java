package io.casehub.agency.summarisation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class KeyedSummarisationRunner<K, IN, OUT> implements Tickable {

    private static final System.Logger LOG = System.getLogger(KeyedSummarisationRunner.class.getName());

    private final KeyedAccumulator<K, IN>        accumulator;
    private final Compactor<IN>                  compactor;
    private final Summariser<IN, OUT>            summariser;
    private final EventStreamBus<OUT>            outputBus;
    private final EventLevel                     outputLevel;
    private final Consumer<List<LevelEvent<IN>>> onFailure;
    private final ConcurrentHashMap<K, Object>   keyState = new ConcurrentHashMap<>();
    private final StateStore<?>                  stateStore;
    private final OutputProcessor<OUT, ?>        outputProcessor;

    public KeyedSummarisationRunner(Function<LevelEvent<IN>, K> keyExtractor,
                                    Predicate<List<LevelEvent<IN>>> completionTest,
                                    long staleTimeout,
                                    Summariser<IN, OUT> summariser,
                                    EventStreamBus<OUT> outputBus,
                                    EventLevel outputLevel) {
        this(keyExtractor, completionTest, staleTimeout, null, summariser, outputBus, outputLevel, null);
    }

    public KeyedSummarisationRunner(Function<LevelEvent<IN>, K> keyExtractor,
                                    Predicate<List<LevelEvent<IN>>> completionTest,
                                    long staleTimeout,
                                    Compactor<IN> compactor,
                                    Summariser<IN, OUT> summariser,
                                    EventStreamBus<OUT> outputBus,
                                    EventLevel outputLevel) {
        this(keyExtractor, completionTest, staleTimeout, compactor, summariser, outputBus, outputLevel, null);
    }

    public KeyedSummarisationRunner(Function<LevelEvent<IN>, K> keyExtractor,
                                    Predicate<List<LevelEvent<IN>>> completionTest,
                                    long staleTimeout,
                                    Summariser<IN, OUT> summariser,
                                    EventStreamBus<OUT> outputBus,
                                    EventLevel outputLevel,
                                    Consumer<List<LevelEvent<IN>>> onFailure) {
        this(keyExtractor, completionTest, staleTimeout, null, summariser, outputBus, outputLevel, onFailure);
    }

    public KeyedSummarisationRunner(Function<LevelEvent<IN>, K> keyExtractor,
                                    Predicate<List<LevelEvent<IN>>> completionTest,
                                    long staleTimeout,
                                    Compactor<IN> compactor,
                                    Summariser<IN, OUT> summariser,
                                    EventStreamBus<OUT> outputBus,
                                    EventLevel outputLevel,
                                    Consumer<List<LevelEvent<IN>>> onFailure) {
        this.accumulator     = new KeyedAccumulator<>(keyExtractor, completionTest, staleTimeout);
        this.compactor       = compactor;
        this.summariser      = summariser;
        this.outputBus       = outputBus;
        this.outputLevel     = outputLevel;
        this.onFailure       = onFailure;
        this.stateStore      = null;
        this.outputProcessor = null;
    }

    KeyedSummarisationRunner(Builder<K, IN, OUT> b) {
        this.accumulator     = new KeyedAccumulator<>(b.keyExtractor, b.completionTest, b.staleTimeout);
        this.compactor       = b.compactor;
        this.summariser      = b.summariser;
        this.outputBus       = b.outputBus;
        this.outputLevel     = b.outputLevel;
        this.onFailure       = b.onFailure;
        this.stateStore      = b.stateStore;
        this.outputProcessor = b.outputProcessor;
    }

    public static <K, IN, OUT> Builder<K, IN, OUT> builder(
            Function<LevelEvent<IN>, K> keyExtractor,
            Predicate<List<LevelEvent<IN>>> completionTest,
            long staleTimeout,
            Summariser<IN, OUT> summariser,
            EventStreamBus<OUT> outputBus,
            EventLevel outputLevel) {
        return new Builder<>(keyExtractor, completionTest, staleTimeout,
                             summariser, outputBus, outputLevel);
    }

    public void collect(LevelEvent<IN> event) {
        accumulator.collect(event);
    }

    public synchronized CompletionStage<Void> tick(long now) {
        var groups = accumulator.drain(now);
        if (groups.isEmpty()) {return CompletableFuture.completedFuture(null);}
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = groups.stream()
                                                  .map(group -> {
                                                      var batch = compactor != null ? compactor.compact(group) : group;
                                                      K   key   = batch.isEmpty() ? null : accumulator.keyExtractor().apply(batch.get(0));
                                                      return invokeSummariser(batch, key, now)
                                                                     .handle((v, ex) -> {
                                                                         if (ex != null) {
                                                                             LOG.log(System.Logger.Level.WARNING,
                                                                                     "Summarisation failed, batch size=" + batch.size(), ex);
                                                                             if (onFailure != null) {
                                                                                 onFailure.accept(batch);
                                                                             }
                                                                         }
                                                                         return (Void) null;
                                                                     }).toCompletableFuture();
                                                  })
                                                  .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    public synchronized CompletionStage<Void> flush() {
        var groups = accumulator.drainAll();
        if (groups.isEmpty()) {return CompletableFuture.completedFuture(null);}
        long now = System.currentTimeMillis();
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = groups.stream()
                                                  .map(group -> {
                                                      var batch = compactor != null ? compactor.compact(group) : group;
                                                      K   key   = batch.isEmpty() ? null : accumulator.keyExtractor().apply(batch.get(0));
                                                      return invokeSummariser(batch, key, now)
                                                                     .handle((v, ex) -> {
                                                                         if (ex != null) {
                                                                             LOG.log(System.Logger.Level.WARNING,
                                                                                     "Flush failed, batch size=" + batch.size(), ex);
                                                                             if (onFailure != null) {
                                                                                 onFailure.accept(batch);
                                                                             }
                                                                         }
                                                                         return (Void) null;
                                                                     }).toCompletableFuture();
                                                  })
                                                  .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<Void> invokeSummariser(List<LevelEvent<IN>> batch, K key, long now) {
        String tenancyId = batch.isEmpty() ? null : batch.get(0).tenancyId();
        if (summariser instanceof StatefulSummariser<IN, OUT, ?> stateful) {
            var    typedStateful = (StatefulSummariser<IN, OUT, Object>) stateful;
            Object prevState     = key != null ? keyState.get(key) : null;
            if (prevState == null && key != null && stateStore != null) {
                prevState = ((StateStore<Object>) stateStore).load(key.toString());
                if (prevState != null) {
                    keyState.put(key, prevState);
                }
            }
            return typedStateful.summarise(batch, prevState).thenAccept(result -> {
                if (result.newState() != null && key != null) {
                    keyState.put(key, result.newState());
                    if (stateStore != null) {
                        ((StateStore<Object>) stateStore).store(key.toString(), result.newState());
                    }
                }
                var outputs = result.outputs();
                if (outputProcessor != null) {
                    outputs = ((OutputProcessor<OUT, Object>) outputProcessor)
                                      .process(outputs, result.newState());
                }
                for (var payload : outputs) {
                    outputBus.publish(new LevelEvent<>(payload, now, outputLevel, tenancyId));
                }
            });
        }
        return summariser.summarise(batch).thenAccept(results -> {
            var outputs = results;
            if (outputProcessor != null) {
                outputs = ((OutputProcessor<OUT, Object>) outputProcessor)
                                  .process(outputs, null);
            }
            for (var payload : outputs) {
                outputBus.publish(new LevelEvent<>(payload, now, outputLevel, tenancyId));
            }
        });
    }

    public void clear() {
        accumulator.clear();
    }

    public int groupCount() {
        return accumulator.groupCount();
    }

    public int eventCount() {
        return accumulator.eventCount();
    }

    public void evictState(K key) {
        keyState.remove(key);
    }

    public static class Builder<K, IN, OUT> {
        final Function<LevelEvent<IN>, K>     keyExtractor;
        final Predicate<List<LevelEvent<IN>>> completionTest;
        final long                            staleTimeout;
        final Summariser<IN, OUT>             summariser;
        final EventStreamBus<OUT>             outputBus;
        final EventLevel                      outputLevel;
        StateStore<?>                  stateStore;
        OutputProcessor<OUT, ?>        outputProcessor;
        Compactor<IN>                  compactor;
        Consumer<List<LevelEvent<IN>>> onFailure;

        Builder(Function<LevelEvent<IN>, K> keyExtractor,
                Predicate<List<LevelEvent<IN>>> completionTest,
                long staleTimeout,
                Summariser<IN, OUT> summariser,
                EventStreamBus<OUT> outputBus,
                EventLevel outputLevel) {
            this.keyExtractor   = keyExtractor;
            this.completionTest = completionTest;
            this.staleTimeout   = staleTimeout;
            this.summariser     = summariser;
            this.outputBus      = outputBus;
            this.outputLevel    = outputLevel;
        }

        public <S> Builder<K, IN, OUT> stateStore(StateStore<S> store) {
            this.stateStore = store;
            return this;
        }

        public <S> Builder<K, IN, OUT> outputProcessor(OutputProcessor<OUT, S> processor) {
            this.outputProcessor = processor;
            return this;
        }

        public Builder<K, IN, OUT> compactor(Compactor<IN> compactor) {
            this.compactor = compactor;
            return this;
        }

        public Builder<K, IN, OUT> onFailure(Consumer<List<LevelEvent<IN>>> onFailure) {
            this.onFailure = onFailure;
            return this;
        }

        public KeyedSummarisationRunner<K, IN, OUT> build() {
            return new KeyedSummarisationRunner<>(this);
        }
    }
}
