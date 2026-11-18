package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.ws.protocol.AvatarMessage;
import io.casehub.agency.speech.ws.protocol.GestureOrigin;
import io.casehub.agency.speech.ws.protocol.MessageCodec;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class SilenceAttentionPolicy {

    private static final System.Logger LOG = System.getLogger("silence-attention");

    private static final List<String> SUBTLE_GESTURES = List.of("interact");
    private static final List<String> STRONG_GESTURES = List.of("knock");

    private final FloorState floorState;
    private final Consumer<String> textSink;
    private final long subtleThresholdMs;
    private final long strongThresholdMs;

    private volatile boolean subtleSent;
    private volatile boolean strongSent;
    private volatile Thread timerThread;

    public SilenceAttentionPolicy(FloorState floorState, Consumer<String> textSink,
                                  int subtleSeconds, int strongSeconds) {
        this.floorState = floorState;
        this.textSink = textSink;
        this.subtleThresholdMs = subtleSeconds * 1000L;
        this.strongThresholdMs = strongSeconds * 1000L;
    }

    public void start() {
        if (timerThread != null) return;
        timerThread = Thread.ofVirtual().name("silence-attention").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                check();
            }
        });
    }

    public void stop() {
        if (timerThread != null) {
            timerThread.interrupt();
            timerThread = null;
        }
    }

    void check() {
        if (floorState.shouldSuppressProactive()) {
            resetBids();
            return;
        }

        long silenceMs = System.currentTimeMillis() - floorState.lastUserActivity();

        if (silenceMs < subtleThresholdMs) {
            resetBids();
            return;
        }

        if (!strongSent && silenceMs >= strongThresholdMs) {
            String gesture = pick(STRONG_GESTURES);
            LOG.log(System.Logger.Level.DEBUG, "Strong attention bid: {0} after {1}s silence",
                    gesture, silenceMs / 1000);
            sendGesture(gesture);
            strongSent = true;
        } else if (!subtleSent && silenceMs >= subtleThresholdMs) {
            String gesture = pick(SUBTLE_GESTURES);
            LOG.log(System.Logger.Level.DEBUG, "Subtle attention bid: {0} after {1}s silence",
                    gesture, silenceMs / 1000);
            sendGesture(gesture);
            subtleSent = true;
        }
    }

    public void resetBids() {
        subtleSent = false;
        strongSent = false;
    }

    private void sendGesture(String tag) {
        textSink.accept(MessageCodec.encode(new AvatarMessage.Gesture(tag, GestureOrigin.ATTENTION)));
    }

    private static String pick(List<String> gestures) {
        return gestures.get(ThreadLocalRandom.current().nextInt(gestures.size()));
    }
}
