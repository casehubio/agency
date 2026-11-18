package io.casehub.agency.speech.ws;

public class FloorState {

    private volatile boolean listening;
    private volatile boolean turnInFlight;
    private volatile long lastUserActivityMs;
    private volatile int unacknowledgedTurnSeq = -1;
    private volatile long unacknowledgedExpiresAt;

    public void onStart() {
        listening = true;
        lastUserActivityMs = System.currentTimeMillis();
    }

    public void onStopOrText() {
        listening = false;
        lastUserActivityMs = System.currentTimeMillis();
    }

    public void onUserActivity() {
        lastUserActivityMs = System.currentTimeMillis();
    }

    public void onTurnStart() {
        turnInFlight = true;
    }

    public void onTurnEnd() {
        turnInFlight = false;
    }

    public void onTurnComplete(int turnSeq, long expiresAt) {
        unacknowledgedTurnSeq = turnSeq;
        unacknowledgedExpiresAt = expiresAt;
    }

    public void onPlaybackComplete(int turnSeq) {
        if (turnSeq == unacknowledgedTurnSeq) {
            unacknowledgedTurnSeq = -1;
        }
    }

    public boolean isListening() {
        return listening;
    }

    public boolean hasUnacknowledgedTurn() {
        return unacknowledgedTurnSeq >= 0;
    }

    long expiresAt() { return unacknowledgedExpiresAt; }

    public boolean isUnacknowledgedExpired(long now) {
        return hasUnacknowledgedTurn() && now >= unacknowledgedExpiresAt;
    }

    public void expireUnacknowledged() {
        unacknowledgedTurnSeq = -1;
        lastUserActivityMs = System.currentTimeMillis();
    }

    public boolean shouldSuppressProactive() {
        return listening || turnInFlight || hasUnacknowledgedTurn();
    }

    public long lastUserActivity() {
        return lastUserActivityMs;
    }
}
