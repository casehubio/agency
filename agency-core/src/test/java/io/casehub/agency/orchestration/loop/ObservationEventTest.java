package io.casehub.agency.orchestration.loop;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class ObservationEventTest {

    @Test
    void validConstruction() {
        var ts = Instant.now();
        var event = new ObservationEvent("key", "value", ts);
        assertThat(event.key()).isEqualTo("key");
        assertThat(event.value()).isEqualTo("value");
        assertThat(event.timestamp()).isEqualTo(ts);
    }

    @Test
    void nullKey_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> new ObservationEvent(null, "v", Instant.now()));
    }

    @Test
    void nullValue_allowed() {
        var event = new ObservationEvent("key", null, Instant.now());
        assertThat(event.value()).isNull();
    }

    @Test
    void factoryMethod_setsTimestamp() {
        var before = Instant.now();
        var event = ObservationEvent.of("key", 42);
        var after = Instant.now();
        assertThat(event.timestamp()).isBetween(before, after);
        assertThat(event.key()).isEqualTo("key");
        assertThat(event.value()).isEqualTo(42);
    }
}
