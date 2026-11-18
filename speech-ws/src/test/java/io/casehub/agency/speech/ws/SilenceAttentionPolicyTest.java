package io.casehub.agency.speech.ws;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SilenceAttentionPolicyTest {

    @Test
    void noGestureBeforeThreshold() {
        var floor = new FloorState();
        floor.onUserActivity();
        var sent = new ArrayList<String>();

        var policy = new SilenceAttentionPolicy(floor, sent::add, 30, 75);
        policy.check();

        assertThat(sent).isEmpty();
    }

    @Test
    void subtleGestureAfterSubtleThreshold() {
        var floor = new FloorState();
        setLastActivity(floor, 31_000);
        var sent = new ArrayList<String>();

        var policy = new SilenceAttentionPolicy(floor, sent::add, 30, 75);
        policy.check();

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).contains("\"type\":\"gesture\"");
        assertThat(sent.get(0)).contains("\"origin\":\"ATTENTION\"");
    }

    @Test
    void strongGestureAfterStrongThreshold() {
        var floor = new FloorState();
        setLastActivity(floor, 76_000);
        var sent = new ArrayList<String>();

        var policy = new SilenceAttentionPolicy(floor, sent::add, 30, 75);
        policy.check();

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).contains("\"tag\":\"knock\"");
    }

    @Test
    void noGestureDuringProactiveSuppression() {
        var floor = new FloorState();
        setLastActivity(floor, 76_000);
        floor.onTurnStart();
        var sent = new ArrayList<String>();

        var policy = new SilenceAttentionPolicy(floor, sent::add, 30, 75);
        policy.check();

        assertThat(sent).isEmpty();
    }

    @Test
    void bidsDoNotRepeat() {
        var floor = new FloorState();
        setLastActivity(floor, 31_000);
        var sent = new ArrayList<String>();

        var policy = new SilenceAttentionPolicy(floor, sent::add, 30, 75);
        policy.check();
        policy.check();
        policy.check();

        assertThat(sent).hasSize(1);
    }

    @Test
    void resetBidsAllowsNewBids() {
        var floor = new FloorState();
        setLastActivity(floor, 31_000);
        var sent = new ArrayList<String>();

        var policy = new SilenceAttentionPolicy(floor, sent::add, 30, 75);
        policy.check();
        assertThat(sent).hasSize(1);

        policy.resetBids();
        policy.check();
        assertThat(sent).hasSize(2);
    }

    private void setLastActivity(FloorState floor, long msAgo) {
        floor.onUserActivity();
        try {
            var field = FloorState.class.getDeclaredField("lastUserActivityMs");
            field.setAccessible(true);
            field.set(floor, System.currentTimeMillis() - msAgo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
