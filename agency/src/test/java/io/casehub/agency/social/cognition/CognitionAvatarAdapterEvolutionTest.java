package io.casehub.agency.social.cognition;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.casehub.agency.social.cognition.evolution.CognitiveEvolutionAdapter;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionCore;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CognitionAvatarAdapterEvolutionTest {

    private final CognitionCore core = mock(CognitionCore.class);
    private final CognitiveEvolutionAdapter evolution = mock(CognitiveEvolutionAdapter.class);

    @Test
    void tick_calls_evolution_adapter_when_present() {
        when(core.config()).thenReturn(CognitionConfig.none());
        var adapter = new CognitionAvatarAdapter(core, null, null, evolution);
        adapter.tick("agent-1", "tenant-1", Set.of("subject-1"));
        verify(evolution).tickIfDue("agent-1", "tenant-1");
    }

    @Test
    void tick_works_without_evolution_adapter() {
        when(core.config()).thenReturn(CognitionConfig.none());
        var adapter = new CognitionAvatarAdapter(core, null, null);
        adapter.tick("agent-1", "tenant-1", Set.of("subject-1"));
        verify(evolution, never()).tickIfDue("agent-1", "tenant-1");
    }
}
