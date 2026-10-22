package io.casehub.agency.summarisation.cloudevents.examples;

import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.SummarisationRunner;
import io.casehub.agency.summarisation.Summariser;
import io.casehub.agency.summarisation.WindowPolicy;
import io.casehub.agency.summarisation.cloudevents.CloudEventEmitter;
import io.casehub.agency.summarisation.cloudevents.CloudEventIngestionAdapter;
import io.casehub.agency.summarisation.cloudevents.PipelineTickScheduler;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates the CloudEvent bridge — full round-trip from CloudEvent ingestion
 * through a summarisation pipeline to CloudEvent emission.
 *
 * Flow: CloudEvent (inbound) → ingestion adapter → EventStreamBus → SummarisationRunner
 *       → output EventStreamBus → CloudEventEmitter → CloudEvent (outbound)
 */
class CloudEventRoundTripExampleTest {

    static final EventLevel INPUT = new EventLevel("scans", 0);
    static final EventLevel OUTPUT = new EventLevel("anomalies", 1);

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(byte[] data) {
        var json = new String(data, StandardCharsets.UTF_8);
        var map = new LinkedHashMap<String, Object>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        for (var entry : json.split(",")) {
            var parts = entry.split(":", 2);
            var key = parts[0].trim().replace("\"", "");
            var val = parts[1].trim().replace("\"", "");
            try { map.put(key, Double.parseDouble(val)); }
            catch (NumberFormatException e) { map.put(key, val); }
        }
        return map;
    }

    @Test
    void cloudEventIngestion_throughPipeline_toCloudEventEmission() {
        var inputBus = new EventStreamBus<Map<String, Object>>();
        var outputBus = new EventStreamBus<Map<String, Object>>();
        var emittedCEs = new ArrayList<CloudEvent>();

        new CloudEventIngestionAdapter<>(inputBus, INPUT,
                "io.casehub.logistics.scan.",
                CloudEventRoundTripExampleTest::parseJson);

        Summariser<Map<String, Object>, Map<String, Object>> passThrough =
                Summariser.ofSync(batch -> batch.stream()
                        .map(LevelEvent::payload)
                        .filter(m -> ((Number) m.get("weight")).doubleValue() > 50.0)
                        .toList());

        var runner = new SummarisationRunner<>(
                WindowPolicy.ofCount(3), passThrough, outputBus, OUTPUT);
        inputBus.subscribe(e -> true, runner::collect);

        new CloudEventEmitter<>(outputBus, emittedCEs::add,
                "io.casehub.logistics.anomaly.v1",
                payload -> payload.toString().getBytes(StandardCharsets.UTF_8));

        // Ingest 3 CloudEvents — 2 heavy packages, 1 light
        ingest(inputBus, "PKG-1", 55.0, "tenant-1");
        ingest(inputBus, "PKG-2", 10.0, "tenant-1");
        ingest(inputBus, "PKG-3", 65.0, "tenant-1");

        runner.tick(1000L).toCompletableFuture().join();

        assertThat(emittedCEs).hasSize(2);
        assertThat(emittedCEs.get(0).getType()).isEqualTo("io.casehub.logistics.anomaly.v1");
        assertThat(emittedCEs.get(0).getExtension("tenancyid")).isEqualTo("tenant-1");
    }

    @Test
    void tenancyId_propagatesThroughPipeline() {
        var inputBus = new EventStreamBus<Map<String, Object>>();
        var outputBus = new EventStreamBus<Map<String, Object>>();
        var emittedCEs = new ArrayList<CloudEvent>();

        new CloudEventIngestionAdapter<>(inputBus, INPUT,
                "io.casehub.logistics.scan.",
                CloudEventRoundTripExampleTest::parseJson);

        var runner = new SummarisationRunner<>(
                WindowPolicy.ofCount(1),
                Summariser.<Map<String, Object>, Map<String, Object>>ofSync(
                        batch -> batch.stream().map(LevelEvent::payload).toList()),
                outputBus, OUTPUT);
        inputBus.subscribe(e -> true, runner::collect);

        new CloudEventEmitter<>(outputBus, emittedCEs::add,
                "io.casehub.logistics.output.v1",
                payload -> "{}".getBytes(StandardCharsets.UTF_8));

        ingest(inputBus, "PKG-1", 10.0, "tenant-A");
        runner.tick(1000L).toCompletableFuture().join();

        ingest(inputBus, "PKG-2", 10.0, "tenant-B");
        runner.tick(2000L).toCompletableFuture().join();

        assertThat(emittedCEs).hasSize(2);
        assertThat(emittedCEs.get(0).getExtension("tenancyid")).isEqualTo("tenant-A");
        assertThat(emittedCEs.get(1).getExtension("tenancyid")).isEqualTo("tenant-B");
    }

    @Test
    void tickScheduler_drivesPipeline() throws Exception {
        var inputBus = new EventStreamBus<Map<String, Object>>();
        var outputBus = new EventStreamBus<Map<String, Object>>();
        var latch = new CountDownLatch(1);

        var runner = new SummarisationRunner<>(
                WindowPolicy.ofCount(1),
                Summariser.<Map<String, Object>, Map<String, Object>>ofSync(
                        batch -> batch.stream().map(LevelEvent::payload).toList()),
                outputBus, OUTPUT);
        inputBus.subscribe(e -> true, runner::collect);
        outputBus.subscribe(e -> true, e -> latch.countDown());

        var scheduler = new PipelineTickScheduler(
                List.of(now -> runner.tick(now)), 50L);
        scheduler.start();

        inputBus.publish(new LevelEvent<>(Map.of("x", (Object) 1), 100L, INPUT, null));
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

        scheduler.stop();
    }

    private void ingest(EventStreamBus<Map<String, Object>> bus,
                         String packageId, double weight, String tenancyId) {
        var adapter = new CloudEventIngestionAdapter<>(bus, INPUT,
                "io.casehub.logistics.scan.",
                CloudEventRoundTripExampleTest::parseJson);

        var ce = CloudEventBuilder.v1()
                .withId(packageId)
                .withType("io.casehub.logistics.scan.v1")
                .withSource(URI.create("/logistics"))
                .withData("application/json",
                        ("{\"packageId\":\"" + packageId + "\",\"weight\":" + weight + "}")
                                .getBytes(StandardCharsets.UTF_8))
                .withExtension("tenancyid", tenancyId)
                .build();
        adapter.accept(ce);
    }
}
