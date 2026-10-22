package io.casehub.agency.summarisation.cloudevents;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PipelineTickScheduler {

    private static final System.Logger LOG = System.getLogger(PipelineTickScheduler.class.getName());

    @FunctionalInterface
    public interface Tickable {
        CompletionStage<Void> tick(long now);

        default CompletionStage<Void> flush() {
            return CompletableFuture.completedFuture(null);
        }
    }

    private final List<Tickable> tickables;
    private final long intervalMs;
    private volatile ScheduledExecutorService executor;

    public PipelineTickScheduler(List<Tickable> tickables, long intervalMs) {
        this.tickables = List.copyOf(Objects.requireNonNull(tickables));
        this.intervalMs = intervalMs;
    }

    public void start() {
        if (executor != null) {
            throw new IllegalStateException("Scheduler already started");
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "summarisation-tick");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::tickAll, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        var exec = this.executor;
        if (exec == null) return;
        this.executor = null;
        exec.shutdown();
        try {
            exec.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        flushAll();
    }

    private void tickAll() {
        long now = System.currentTimeMillis();
        for (var tickable : tickables) {
            try {
                tickable.tick(now).toCompletableFuture().join();
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Tick failed", e);
            }
        }
    }

    private void flushAll() {
        for (var tickable : tickables) {
            try {
                tickable.flush().toCompletableFuture().join();
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Flush failed", e);
            }
        }
    }
}
