package io.casehub.agency.summarisation.cloudevents;

import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.agency.summarisation.LevelEvent;
import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CloudEventEmitterTest {

    static final EventLevel LEVEL = new EventLevel("output", 1);

    private static byte[] toJson(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            var sb = new StringBuilder("{");
            var first = true;
            for (var entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":\"")
                        .append(entry.getValue()).append("\"");
                first = false;
            }
            sb.append("}");
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void emits_cloudEventFromLevelEvent() {
        var bus = new EventStreamBus<Map<String, Object>>();
        var emitted = new ArrayList<CloudEvent>();

        new CloudEventEmitter<>(bus, emitted::add,
                "io.casehub.test.output.v1",
                CloudEventEmitterTest::toJson);

        bus.publish(new LevelEvent<>(Map.of("key", "val"), 100L, LEVEL, "tenant-1"));

        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).getType()).isEqualTo("io.casehub.test.output.v1");
        assertThat(emitted.get(0).getExtension("tenancyid")).isEqualTo("tenant-1");
        assertThat(emitted.get(0).getData()).isNotNull();
    }

    @Test
    void omits_tenancyIdExtension_whenNull() {
        var bus = new EventStreamBus<Map<String, Object>>();
        var emitted = new ArrayList<CloudEvent>();

        new CloudEventEmitter<>(bus, emitted::add,
                "io.casehub.test.output.v1",
                CloudEventEmitterTest::toJson);

        bus.publish(new LevelEvent<>(Map.of("a", "b"), 200L, LEVEL, null));

        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).getExtension("tenancyid")).isNull();
    }

    @Test
    void setsTimestamp_fromLevelEvent() {
        var bus = new EventStreamBus<Map<String, Object>>();
        var emitted = new ArrayList<CloudEvent>();

        new CloudEventEmitter<>(bus, emitted::add,
                "io.casehub.test.output.v1",
                CloudEventEmitterTest::toJson);

        bus.publish(new LevelEvent<>(Map.of("x", "y"), 1_000_000L, LEVEL, null));

        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).getTime()).isNotNull();
        assertThat(emitted.get(0).getTime().toInstant().toEpochMilli())
                .isEqualTo(1_000_000L);
    }

    @Test
    void emits_multipleEvents() {
        var bus = new EventStreamBus<Map<String, Object>>();
        var emitted = new ArrayList<CloudEvent>();

        new CloudEventEmitter<>(bus, emitted::add,
                "io.casehub.test.output.v1",
                CloudEventEmitterTest::toJson);

        bus.publish(new LevelEvent<>(Map.of("a", "1"), 100L, LEVEL, "t1"));
        bus.publish(new LevelEvent<>(Map.of("b", "2"), 200L, LEVEL, "t2"));

        assertThat(emitted).hasSize(2);
        assertThat(emitted.get(0).getExtension("tenancyid")).isEqualTo("t1");
        assertThat(emitted.get(1).getExtension("tenancyid")).isEqualTo("t2");
    }
}
