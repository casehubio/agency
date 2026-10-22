package io.casehub.agency.summarisation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LevelEventTest {

    static final EventLevel LEVEL = new EventLevel("test", 1);

    @Test
    void tenancyId_presentWhenProvided() {
        var event = new LevelEvent<>("payload", 100L, LEVEL, "tenant-1");
        assertThat(event.tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void tenancyId_nullForSingleTenant() {
        var event = new LevelEvent<>("payload", 100L, LEVEL, null);
        assertThat(event.tenancyId()).isNull();
    }

    @Test
    void existingComponentsPreserved() {
        var event = new LevelEvent<>("data", 42L, LEVEL, "t1");
        assertThat(event.payload()).isEqualTo("data");
        assertThat(event.timestamp()).isEqualTo(42L);
        assertThat(event.level()).isEqualTo(LEVEL);
    }
}
