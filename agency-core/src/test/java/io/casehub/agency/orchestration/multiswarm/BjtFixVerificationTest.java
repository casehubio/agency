package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkmind.inference.bayes.OnlineParameterUpdater;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BjtFixVerificationTest {

    @Test
    void verify_cpt_preserved_after_single_update() {
        var network = RelevanceNetworks.standard();
        var updater = OnlineParameterUpdater.create(network, 0.95);

        double[][] cptBefore = updater.getCpt("Relevant");
        System.out.println("CPT row 0 before: [" + cptBefore[0][0] + ", " + cptBefore[0][1] + "]");
        System.out.println("CPT row 10 before: [" + cptBefore[10][0] + ", " + cptBefore[10][1] + "]");

        updater.update(Map.of("CapabilityOverlap", "medium", "DomainMatch", "partial",
                "SignalStrength", "moderate", "Relevant", "yes"));

        double[][] cptAfter = updater.getCpt("Relevant");
        System.out.println("CPT row 0 after: [" + cptAfter[0][0] + ", " + cptAfter[0][1] + "]");
        System.out.println("CPT row 10 after: [" + cptAfter[10][0] + ", " + cptAfter[10][1] + "]");

        // Row 0 should NOT have changed to 0.5/0.5 — it should be close to original
        assertThat(cptAfter[0][0]).as("Row 0 should preserve original CPT, not reset to 0.5")
                .isGreaterThan(0.6);

        // Score after single update
        var instance = updater.getInstance();
        instance.setLikelyhood("CapabilityOverlap", new double[]{0.15, 0.7, 0.15});
        instance.setLikelyhood("DomainMatch", new double[]{0.15, 0.7, 0.15});
        instance.setLikelyhood("SignalStrength", new double[]{0.15, 0.7, 0.15});
        instance.setLikelyhood("HistoricalRelevance", new double[]{0.5, 0.5});
        instance.globalUpdate();
        double p = instance.marginalize("Relevant").getDistribution()[0];
        System.out.println("P(relevant) after 1 update: " + p);
        assertThat(p).as("Should not be zero after one update").isGreaterThan(0.01);
    }
}
