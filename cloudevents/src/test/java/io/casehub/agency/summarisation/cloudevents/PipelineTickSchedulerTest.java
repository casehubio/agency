package io.casehub.agency.summarisation.cloudevents;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineTickSchedulerTest {

    @Test
    void ticks_allTickables_atInterval() throws Exception {
        var tickCount = new AtomicInteger();
        var latch = new CountDownLatch(3);
        PipelineTickScheduler.Tickable tickable = now -> {
            tickCount.incrementAndGet();
            latch.countDown();
            return CompletableFuture.completedFuture(null);
        };

        var scheduler = new PipelineTickScheduler(List.of(tickable), 50L);
        scheduler.start();
        latch.await(2, TimeUnit.SECONDS);
        scheduler.stop();

        assertThat(tickCount.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void ticks_multipleTickables() throws Exception {
        var count1 = new AtomicInteger();
        var count2 = new AtomicInteger();
        var latch = new CountDownLatch(4);

        PipelineTickScheduler.Tickable t1 = now -> {
            count1.incrementAndGet();
            latch.countDown();
            return CompletableFuture.completedFuture(null);
        };
        PipelineTickScheduler.Tickable t2 = now -> {
            count2.incrementAndGet();
            latch.countDown();
            return CompletableFuture.completedFuture(null);
        };

        var scheduler = new PipelineTickScheduler(List.of(t1, t2), 50L);
        scheduler.start();
        latch.await(2, TimeUnit.SECONDS);
        scheduler.stop();

        assertThat(count1.get()).isGreaterThanOrEqualTo(2);
        assertThat(count2.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void stop_flushesAllTickables() throws Exception {
        var flushed = new AtomicInteger();
        PipelineTickScheduler.Tickable tickable = new PipelineTickScheduler.Tickable() {
            @Override
            public CompletionStage<Void> tick(long now) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> flush() {
                flushed.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }
        };

        var scheduler = new PipelineTickScheduler(List.of(tickable), 1000L);
        scheduler.start();
        scheduler.stop();

        assertThat(flushed.get()).isEqualTo(1);
    }

    @Test
    void tolerates_tickableException() throws Exception {
        var tickCount = new AtomicInteger();
        var latch = new CountDownLatch(2);

        PipelineTickScheduler.Tickable failing = now -> {
            tickCount.incrementAndGet();
            latch.countDown();
            return CompletableFuture.failedFuture(new RuntimeException("boom"));
        };

        var scheduler = new PipelineTickScheduler(List.of(failing), 50L);
        scheduler.start();
        latch.await(2, TimeUnit.SECONDS);
        scheduler.stop();

        assertThat(tickCount.get()).isGreaterThanOrEqualTo(2);
    }
}
