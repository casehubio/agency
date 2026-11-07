package io.casehub.agency.deliberation.coalition.formation;

import io.casehub.agency.deliberation.intention.IntentionMonitor;
import io.casehub.agency.deliberation.intention.IntentionStatus;
import io.casehub.agency.deliberation.intention.ReconsiderationReason;
import io.casehub.agency.deliberation.intention.ReconsiderationSignal;
import io.casehub.eidos.org.api.GroupLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FormationActivatorTest {

    private GroupLifecycleService groupLifecycle;

    @BeforeEach
    void setUp() {
        groupLifecycle = mock(GroupLifecycleService.class);
    }

    @Test
    void noMonitor_activatesUnconditionally() {
        var activator = new FormationActivator(groupLifecycle, null);

        var result = activator.activate("group-1", Set.of("agent-a", "agent-b"),
                "Execute task", "tenant-1");

        assertThat(result).isInstanceOf(ActivationResult.Activated.class);
        var activated = (ActivationResult.Activated) result;
        assertThat(activated.groupInstanceId()).isEqualTo("group-1");
        assertThat(activated.intention().status()).isEqualTo(IntentionStatus.ACTIVE);
        assertThat(activated.intention().committedParties()).containsExactlyInAnyOrder("agent-a", "agent-b");
        verify(groupLifecycle).activateGroup("group-1", "tenant-1");
    }

    @Test
    void monitorApproves_activatesGroup() {
        var monitor = mock(IntentionMonitor.class);
        when(monitor.evaluate(any())).thenReturn(null);
        var activator = new FormationActivator(groupLifecycle, monitor);

        var result = activator.activate("group-1", Set.of("agent-a"),
                "Execute task", "tenant-1");

        assertThat(result).isInstanceOf(ActivationResult.Activated.class);
        verify(groupLifecycle).activateGroup("group-1", "tenant-1");
        verify(monitor).evaluate(any());
    }

    @Test
    void monitorRejects_withShouldDrop_returnsMonitorRejected() {
        var signal = ReconsiderationSignal.drop(
                ReconsiderationReason.PRECONDITION_VIOLATED, "capability lost");
        var monitor = mock(IntentionMonitor.class);
        when(monitor.evaluate(any())).thenReturn(signal);
        var activator = new FormationActivator(groupLifecycle, monitor);

        var result = activator.activate("group-1", Set.of("agent-a"),
                "Execute task", "tenant-1");

        assertThat(result).isInstanceOf(ActivationResult.MonitorRejected.class);
        var rejected = (ActivationResult.MonitorRejected) result;
        assertThat(rejected.signal().shouldDrop()).isTrue();
        assertThat(rejected.signal().reason()).isEqualTo(ReconsiderationReason.PRECONDITION_VIOLATED);
        verifyNoInteractions(groupLifecycle);
    }

    @Test
    void monitorReconsiders_withoutShouldDrop_stillRejects() {
        var signal = ReconsiderationSignal.reconsider(
                ReconsiderationReason.CONTEXT_CHANGED, "environment shifted");
        var monitor = mock(IntentionMonitor.class);
        when(monitor.evaluate(any())).thenReturn(signal);
        var activator = new FormationActivator(groupLifecycle, monitor);

        var result = activator.activate("group-1", Set.of("agent-a"),
                "Execute task", "tenant-1");

        assertThat(result).isInstanceOf(ActivationResult.MonitorRejected.class);
        assertThat(((ActivationResult.MonitorRejected) result).signal().shouldDrop()).isFalse();
        verifyNoInteractions(groupLifecycle);
    }

    @Test
    void activateGroupFailure_returnsActivationFailed() {
        doThrow(new RuntimeException("group not found"))
                .when(groupLifecycle).activateGroup(any(), any());
        var activator = new FormationActivator(groupLifecycle, null);

        var result = activator.activate("group-1", Set.of("agent-a"),
                "Execute task", "tenant-1");

        assertThat(result).isInstanceOf(ActivationResult.ActivationFailed.class);
        assertThat(((ActivationResult.ActivationFailed) result).reason())
                .isEqualTo("group not found");
    }

    @Test
    void activatedIntention_hasCorrectFields() {
        var activator = new FormationActivator(groupLifecycle, null);
        var members = Set.of("agent-a", "agent-b", "agent-c");

        var result = activator.activate("group-42", members,
                "Coalition for analysis", "tenant-1");

        assertThat(result).isInstanceOf(ActivationResult.Activated.class);
        var activated = (ActivationResult.Activated) result;
        assertThat(activated.intention().intentionId()).isEqualTo("group-42");
        assertThat(activated.intention().planDescription()).isEqualTo("Coalition for analysis");
        assertThat(activated.intention().committedParties()).isEqualTo(members);
        assertThat(activated.intention().status()).isEqualTo(IntentionStatus.ACTIVE);
        assertThat(activated.intention().formedAt()).isNotNull();
        assertThat(activated.intention().dropReason()).isNull();
    }

    @Test
    void nullGroupInstanceId_throwsNPE() {
        var activator = new FormationActivator(groupLifecycle, null);

        assertThatThrownBy(() -> activator.activate(null, Set.of("a"), "plan", "t"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullMemberNames_throwsNPE() {
        var activator = new FormationActivator(groupLifecycle, null);

        assertThatThrownBy(() -> activator.activate("g", null, "plan", "t"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullConstructorGroupLifecycle_throwsNPE() {
        assertThatThrownBy(() -> new FormationActivator(null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
