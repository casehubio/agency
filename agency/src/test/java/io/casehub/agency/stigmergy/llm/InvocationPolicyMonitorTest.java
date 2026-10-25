package io.casehub.agency.stigmergy.llm;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class InvocationPolicyMonitorTest {

    @Test
    void logsNothing_whenDurationWithinExpected() {
        var policy = LlmInvocationPolicy.of(
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30));
        var records = captureLogRecords(() ->
            InvocationPolicyMonitor.check(policy, Duration.ofSeconds(3), "observation"));
        assertThat(records).isEmpty();
    }

    @Test
    void logsWarning_whenDurationExceedsWarnThreshold() {
        var policy = LlmInvocationPolicy.of(
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30));
        var records = captureLogRecords(() ->
            InvocationPolicyMonitor.check(policy, Duration.ofSeconds(15), "observation"));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getLevel()).isEqualTo(Level.WARNING);
        assertThat(records.get(0).getMessage()).contains("observation");
        assertThat(records.get(0).getMessage()).contains("15");
    }

    @Test
    void logsSevere_whenDurationExceedsErrorThreshold() {
        var policy = LlmInvocationPolicy.of(
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30));
        var records = captureLogRecords(() ->
            InvocationPolicyMonitor.check(policy, Duration.ofSeconds(45), "observation"));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getLevel()).isEqualTo(Level.SEVERE);
        assertThat(records.get(0).getMessage()).contains("observation");
    }

    @Test
    void severeTakesPrecedenceOverWarning() {
        var policy = LlmInvocationPolicy.of(
            Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5));
        var records = captureLogRecords(() ->
            InvocationPolicyMonitor.check(policy, Duration.ofSeconds(10), "test"));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getLevel()).isEqualTo(Level.SEVERE);
    }

    @Test
    void returnsExceeded_whenErrorThresholdBreached() {
        var policy = LlmInvocationPolicy.of(
            Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5));
        var result = InvocationPolicyMonitor.check(policy, Duration.ofSeconds(10), "test");
        assertThat(result).isTrue();
    }

    @Test
    void returnsNotExceeded_whenWithinThresholds() {
        var policy = LlmInvocationPolicy.of(
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30));
        var result = InvocationPolicyMonitor.check(policy, Duration.ofSeconds(3), "test");
        assertThat(result).isFalse();
    }

    @Test
    void returnsNotExceeded_whenOnlyWarnThresholdBreached() {
        var policy = LlmInvocationPolicy.of(
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30));
        var result = InvocationPolicyMonitor.check(policy, Duration.ofSeconds(15), "test");
        assertThat(result).isFalse();
    }

    private List<LogRecord> captureLogRecords(Runnable action) {
        var logger = Logger.getLogger(InvocationPolicyMonitor.class.getName());
        var records = new ArrayList<LogRecord>();
        var handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(handler);
        var oldLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(oldLevel);
        }
        return records;
    }
}
