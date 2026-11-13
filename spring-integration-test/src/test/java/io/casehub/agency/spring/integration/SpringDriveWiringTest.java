package io.casehub.agency.spring.integration;

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
import io.casehub.agency.spring.AgencyAutoConfiguration;
import java.util.Optional;

class SpringDriveWiringTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void driveTickWorksWithOptionalMindMap(boolean storePresent) {
        var store = mock(MindMapStore.class);
        var drives = new AgencyAutoConfiguration().driveOrchestrator(
                storePresent ? Optional.of(store) : Optional.empty(),
                mock(StrategyLearningOrchestrator.class), mock(UserModelOrchestrator.class),
                mock(MentalModelOrchestrator.class), mock(MoodOrchestrator.class),
                new DriveComposer(), DriveConfig.defaults(), Optional.empty());
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
