package io.casehub.agency.speech.ws;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FloorStateTest {

    @Test
    void initialStateIsNotListeningAndNoUnacknowledged() {
        var state = new FloorState();
        assertThat(state.isListening()).isFalse();
        assertThat(state.hasUnacknowledgedTurn()).isFalse();
        assertThat(state.shouldSuppressProactive()).isFalse();
    }

    @Test
    void onStartSetsListening() {
        var state = new FloorState();
        state.onStart();
        assertThat(state.isListening()).isTrue();
        assertThat(state.shouldSuppressProactive()).isTrue();
    }

    @Test
    void onStopClearsListening() {
        var state = new FloorState();
        state.onStart();
        state.onStopOrText();
        assertThat(state.isListening()).isFalse();
    }

    @Test
    void turnCompleteSetsUnacknowledged() {
        var state = new FloorState();
        long future = System.currentTimeMillis() + 60_000;
        state.onTurnComplete(1, future);

        assertThat(state.hasUnacknowledgedTurn()).isTrue();
        assertThat(state.shouldSuppressProactive()).isTrue();
    }

    @Test
    void playbackCompleteClearsMatchingTurn() {
        var state = new FloorState();
        state.onTurnComplete(1, System.currentTimeMillis() + 60_000);
        state.onPlaybackComplete(1);

        assertThat(state.hasUnacknowledgedTurn()).isFalse();
        assertThat(state.shouldSuppressProactive()).isFalse();
    }

    @Test
    void playbackCompleteIgnoresMismatchedTurnSeq() {
        var state = new FloorState();
        state.onTurnComplete(1, System.currentTimeMillis() + 60_000);
        state.onPlaybackComplete(99);

        assertThat(state.hasUnacknowledgedTurn()).isTrue();
    }

    @Test
    void expiredUnacknowledgedTurnDetected() {
        var state = new FloorState();
        long past = System.currentTimeMillis() - 1000;
        state.onTurnComplete(1, past);

        assertThat(state.isUnacknowledgedExpired(System.currentTimeMillis())).isTrue();
    }

    @Test
    void nonExpiredUnacknowledgedTurnNotDetected() {
        var state = new FloorState();
        long future = System.currentTimeMillis() + 60_000;
        state.onTurnComplete(1, future);

        assertThat(state.isUnacknowledgedExpired(System.currentTimeMillis())).isFalse();
    }

    @Test
    void expireUnacknowledgedClearsState() {
        var state = new FloorState();
        state.onTurnComplete(1, System.currentTimeMillis() - 1000);
        state.expireUnacknowledged();

        assertThat(state.hasUnacknowledgedTurn()).isFalse();
        assertThat(state.shouldSuppressProactive()).isFalse();
    }

    @Test
    void onUserActivityUpdatesTimestamp() {
        var state = new FloorState();
        long before = state.lastUserActivity();
        state.onUserActivity();
        assertThat(state.lastUserActivity()).isGreaterThanOrEqualTo(before);
    }

    @Test
    void turnInFlightSuppressesProactive() {
        var state = new FloorState();
        state.onTurnStart();
        assertThat(state.shouldSuppressProactive()).isTrue();
        state.onTurnEnd();
        assertThat(state.shouldSuppressProactive()).isFalse();
    }

    @Test
    void multipleSuppressConditionsCompound() {
        var state = new FloorState();
        state.onStart();
        state.onTurnStart();
        state.onTurnComplete(1, System.currentTimeMillis() + 60_000);

        assertThat(state.shouldSuppressProactive()).isTrue();

        state.onStopOrText();
        assertThat(state.shouldSuppressProactive()).as("turn in flight").isTrue();

        state.onTurnEnd();
        assertThat(state.shouldSuppressProactive()).as("unacknowledged turn").isTrue();

        state.onPlaybackComplete(1);
        assertThat(state.shouldSuppressProactive()).isFalse();
    }

    @Test
    void expireUnacknowledgedResetsLastActivity() {
        var state = new FloorState();
        state.onTurnComplete(1, System.currentTimeMillis() - 1000);
        long beforeExpiry = state.lastUserActivity();
        state.expireUnacknowledged();
        assertThat(state.lastUserActivity()).isGreaterThanOrEqualTo(beforeExpiry);
        assertThat(state.hasUnacknowledgedTurn()).isFalse();
    }

}
