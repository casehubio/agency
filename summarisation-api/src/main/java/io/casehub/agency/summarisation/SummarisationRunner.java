package io.casehub.agency.summarisation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class SummarisationRunner<IN, OUT> implements Tickable {

    private static final System.Logger LOG               = System.getLogger(SummarisationRunner.class.getName());
    private static final String        DEFAULT_PARTITION = "__default__";

    private final EventAccumulator<IN>                   accumulator;
    private final Compactor<IN>                          compactor;
    private final Summariser<IN, OUT>                    summariser;
    private final EventStreamBus<OUT>                    outputBus;
    private final EventLevel                             outputLevel;
    private final Consumer<List<LevelEvent<IN>>>         onFailure;
    private final ConcurrentHashMap<String, Object>      partitionState = new ConcurrentHashMap<>();
    private final EmissionPolicy<IN, ?>                  emissionPolicy;
    private final StateStore<?>                          stateStore;
    private final Function<List<LevelEvent<IN>>, String> stateKeyResolver;
    private final OutputProcessor<OUT, ?>                outputProcessor;

    public SummarisationRunner(WindowPolicy policy,
                               Summariser<IN, OUT> summariser,
                               EventStreamBus<OUT> outputBus,
                               EventLevel outputLevel) {
        this(policy, null, summariser, outputBus, outputLevel, null);
    }

    public SummarisationRunner(WindowPolicy policy,
                               Compactor<IN> compactor,
                               Summariser<IN, OUT> summariser,
                               EventStreamBus<OUT> outputBus,
                               EventLevel outputLevel) {
        this(policy, compactor, summariser, outputBus, outputLevel, null);
    }

    public SummarisationRunner(WindowPolicy policy,
                               Summariser<IN, OUT> summariser,
                               EventStreamBus<OUT> outputBus,
                               EventLevel outputLevel,
                               Consumer<List<LevelEvent<IN>>> onFailure) {
        this(policy, null, summariser, outputBus, outputLevel, onFailure);
    }

    public SummarisationRunner(WindowPolicy policy,
                               Compactor<IN> compactor,
                               Summariser<IN, OUT> summariser,
                               EventStreamBus<OUT> outputBus,
                               EventLevel outputLevel,
                               Consumer<List<LevelEvent<IN>>> onFailure) {
        this.accumulator      = new EventAccumulator<>(policy);
        this.compactor        = compactor;
        this.summariser       = summariser;
        this.outputBus        = outputBus;
        this.outputLevel      = outputLevel;
        this.onFailure        = onFailure;
        this.emissionPolicy   = null;
        this.stateStore       = null;
        this.stateKeyResolver = null;
        this.outputProcessor  = null;
    }

    SummarisationRunner(Builder<IN, OUT> b) {
        this.accumulator      = new EventAccumulator<>(WindowPolicy.ofCount(Integer.MAX_VALUE));
        this.compactor        = b.compactor;
        this.summariser       = b.summariser;
        this.outputBus        = b.outputBus;
        this.outputLevel      = b.outputLevel;
        this.onFailure        = b.onFailure;
        this.emissionPolicy   = b.emissionPolicy != null
                                ? b.emissionPolicy
                                : new WindowPolicyEmission<>(b.windowPolicy);
        this.stateStore       = b.stateStore;
        this.stateKeyResolver = b.stateKeyResolver;
        this.outputProcessor  = b.outputProcessor;
    }

    public static <IN, OUT> Builder<IN, OUT> builder(
            Summariser<IN, OUT> summariser,
            EventStreamBus<OUT> outputBus,
            EventLevel outputLevel) {
        return new Builder<>(summariser, outputBus, outputLevel);
    }

    public void collect(LevelEvent<IN> event) {
        accumulator.collect(event);
    }

    public synchronized CompletionStage<Void> tick(long now) {
        if (emissionPolicy != null) {
            return tickWithPolicy(now);
        }
        return tickLegacy(now);
    }

    private CompletionStage<Void> tickLegacy(long now) {
        var batch = accumulator.drainIfReady(now);
        if (batch.isEmpty()) {return CompletableFuture.completedFuture(null);}
        if (compactor != null) {
            batch = compactor.compact(batch);
        }
        var finalBatch = batch;
        return invokeSummariser(finalBatch, now).handle((v, ex) -> {
            if (ex != null) {
                LOG.log(System.Logger.Level.WARNING,
                        "Summarisation failed, batch size=" + finalBatch.size(), ex);
                if (onFailure != null) {
                    onFailure.accept(finalBatch);
                }
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<Void> tickWithPolicy(long now) {
        var buffered = accumulator.peekBuffer();
        if (buffered.isEmpty()) {return CompletableFuture.completedFuture(null);}
        String partitionKey = resolvePartitionKey(buffered);
        Object state        = resolveState(partitionKey);
        var    typedPolicy  = (EmissionPolicy<IN, Object>) emissionPolicy;
        if (!typedPolicy.shouldEmit(buffered, state, now)) {
            return CompletableFuture.completedFuture(null);
        }
        var batch = accumulator.drain();
        if (compactor != null) {
            batch = compactor.compact(batch);
        }
        var finalBatch = batch;
        return invokeSummariser(finalBatch, now).handle((v, ex) -> {
            if (ex != null) {
                LOG.log(System.Logger.Level.WARNING,
                        "Summarisation failed, batch size=" + finalBatch.size(), ex);
                if (onFailure != null) {
                    onFailure.accept(finalBatch);
                }
            }
            return null;
        });
    }

    public synchronized CompletionStage<Void> flush() {
        var batch = accumulator.drain();
        if (batch.isEmpty()) {return CompletableFuture.completedFuture(null);}
        if (compactor != null) {
            batch = compactor.compact(batch);
        }
        var  finalBatch = batch;
        long now        = System.currentTimeMillis();
        return invokeSummariser(finalBatch, now).handle((v, ex) -> {
            if (ex != null) {
                LOG.log(System.Logger.Level.WARNING,
                        "Flush failed, batch size=" + finalBatch.size(), ex);
                if (onFailure != null) {
                    onFailure.accept(finalBatch);
                }
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<Void> invokeSummariser(List<LevelEvent<IN>> batch, long now) {
        String tenancyId    = batch.isEmpty() ? null : batch.get(0).tenancyId();
        String partitionKey = resolvePartitionKey(batch);
        if (summariser instanceof StatefulSummariser<IN, OUT, ?> stateful) {
            var    typedStateful = (StatefulSummariser<IN, OUT, Object>) stateful;
            Object prevState     = resolveState(partitionKey);
            return typedStateful.summarise(batch, prevState).thenAccept(result -> {
                if (result.newState() != null) {
                    partitionState.put(partitionKey, result.newState());
                    if (stateStore != null) {
                        ((StateStore<Object>) stateStore).store(partitionKey, result.newState());
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

    @SuppressWarnings("unchecked")
    private Object resolveState(String partitionKey) {
        Object state = partitionState.get(partitionKey);
        if (state == null && stateStore != null) {
            state = ((StateStore<Object>) stateStore).load(partitionKey);
            if (state != null) {
                partitionState.put(partitionKey, state);
            }
        }
        return state;
    }

    private String resolvePartitionKey(List<LevelEvent<IN>> batch) {
        if (stateKeyResolver != null && !batch.isEmpty()) {
            return stateKeyResolver.apply(batch);
        }
        if (batch.isEmpty()) {return DEFAULT_PARTITION;}
        String tenancyId = batch.get(0).tenancyId();
        return tenancyId != null ? tenancyId : DEFAULT_PARTITION;
    }

    public void clear() {
        accumulator.clear();
    }

    public int size() {
        return accumulator.size();
    }

    public static class Builder<IN, OUT> {
        final Summariser<IN, OUT> summariser;
        final EventStreamBus<OUT> outputBus;
        final EventLevel          outputLevel;
        WindowPolicy                           windowPolicy;
        EmissionPolicy<IN, ?>                  emissionPolicy;
        StateStore<?>                          stateStore;
        Function<List<LevelEvent<IN>>, String> stateKeyResolver;
        OutputProcessor<OUT, ?>                outputProcessor;
        Compactor<IN>                          compactor;
        Consumer<List<LevelEvent<IN>>>         onFailure;

        Builder(Summariser<IN, OUT> summariser,
                EventStreamBus<OUT> outputBus,
                EventLevel outputLevel) {
            this.summariser  = summariser;
            this.outputBus   = outputBus;
            this.outputLevel = outputLevel;
        }

        public Builder<IN, OUT> windowPolicy(WindowPolicy policy) {
            this.windowPolicy = policy;
            return this;
        }

        public <S> Builder<IN, OUT> emissionPolicy(EmissionPolicy<IN, S> policy) {
            this.emissionPolicy = policy;
            return this;
        }

        public <S> Builder<IN, OUT> stateStore(StateStore<S> store) {
            this.stateStore = store;
            return this;
        }

        public Builder<IN, OUT> stateKeyResolver(
                Function<List<LevelEvent<IN>>, String> resolver) {
            this.stateKeyResolver = resolver;
            return this;
        }

        public <S> Builder<IN, OUT> outputProcessor(OutputProcessor<OUT, S> processor) {
            this.outputProcessor = processor;
            return this;
        }

        public Builder<IN, OUT> compactor(Compactor<IN> compactor) {
            this.compactor = compactor;
            return this;
        }

        public Builder<IN, OUT> onFailure(Consumer<List<LevelEvent<IN>>> onFailure) {
            this.onFailure = onFailure;
            return this;
        }

        public SummarisationRunner<IN, OUT> build() {
            if (windowPolicy == null && emissionPolicy == null) {
                throw new IllegalStateException(
                        "Either windowPolicy or emissionPolicy must be set");
            }
            if (windowPolicy != null && emissionPolicy != null) {
                throw new IllegalStateException(
                        "Cannot set both windowPolicy and emissionPolicy");
            }
            return new SummarisationRunner<>(this);
        }
    }
}
