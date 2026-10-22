package io.casehub.agency.orchestration.execution;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;

class ExecutionEventListenerContractTest {

    @Test
    void anonymousImplementationCompiles() {
        ExecutionEventListener listener = new ExecutionEventListener() {};
        assertThatCode(() -> listener.onExecutionStart(null)).doesNotThrowAnyException();
        assertThatCode(() -> listener.onExecutionComplete(null, Duration.ZERO, 0))
                .doesNotThrowAnyException();
    }
}
