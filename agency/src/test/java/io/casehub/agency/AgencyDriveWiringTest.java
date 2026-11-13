package io.casehub.agency;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveComposer;
import io.casehub.neocortex.cognition.drive.DriveConfig;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.strategy.StrategyLearningOrchestrator;
import io.casehub.neocortex.cognition.usermodel.UserModelOrchestrator;
import io.casehub.neocortex.mindmap.MindMapStore;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgencyDriveWiringTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void driveTickWorksWithOptionalMindMap(boolean storePresent) {
        var beans = new AgencyBeans();
        var store = mock(MindMapStore.class);
        beans.mindMapStoreInstance = mock(jakarta.enterprise.inject.Instance.class);
        when(beans.mindMapStoreInstance.isResolvable()).thenReturn(storePresent);
        if (storePresent) when(beans.mindMapStoreInstance.get()).thenReturn(store);
        beans.narrativeOrchestratorInstance = mock(jakarta.enterprise.inject.Instance.class);

        var drives = beans.driveOrchestrator(
                mock(StrategyLearningOrchestrator.class), mock(UserModelOrchestrator.class),
                mock(MentalModelOrchestrator.class), mock(MoodOrchestrator.class),
                new DriveComposer(), DriveConfig.defaults());
        drives.tick("agent", "tenant", AgentDescriptor.builder().agentId("agent").name("Agent")
                .slot("worker").tenancyId("tenant").build());

        assertThat(drives.currentDrives("agent", "tenant").orElseThrow()
                .drives().get(DriveAxis.CURIOSITY).intensity()).isZero();
        if (storePresent) {
            verify(store).listSubgraphs("tenant");
        } else {
            verifyNoInteractions(store);
        }
    }
}
