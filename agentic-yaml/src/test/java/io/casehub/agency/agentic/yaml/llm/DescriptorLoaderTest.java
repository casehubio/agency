package io.casehub.agency.core.yaml.llm;

import io.casehub.eidos.api.DispositionAxis;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptorLoaderTest {

    @Test
    void loadsHistoricalEncounterDescriptors() {
        var descriptors = DescriptorLoader.load("historical-encounter");

        assertThat(descriptors).hasSize(3);
        assertThat(descriptors.get(0).name()).isEqualTo("leonardo");
        assertThat(descriptors.get(1).name()).isEqualTo("nikola");
        assertThat(descriptors.get(2).name()).isEqualTo("historian-narrator");
    }

    @Test
    void descriptorsHaveBriefings() {
        var descriptors = DescriptorLoader.load("historical-encounter");

        for (var d : descriptors) {
            assertThat(d.briefing()).as("briefing for %s", d.name()).isNotBlank();
        }
    }

    @Test
    void descriptorsHaveCapabilities() {
        var descriptors = DescriptorLoader.load("historical-encounter");
        var leonardo = descriptors.get(0);

        assertThat(leonardo.capabilities()).isNotEmpty();
        assertThat(leonardo.capabilities().stream()
                .anyMatch(c -> c.name().equals("art-and-design"))).isTrue();
    }

    @Test
    void capabilitiesHaveQualityHints() {
        var descriptors = DescriptorLoader.load("historical-encounter");
        var leonardo = descriptors.get(0);

        var artCap = leonardo.capabilities().stream()
                .filter(c -> c.name().equals("art-and-design"))
                .findFirst().orElseThrow();
        assertThat(artCap.qualityHint()).isEqualTo(0.95);

        var hydroCap = leonardo.capabilities().stream()
                .filter(c -> c.name().equals("hydrodynamics"))
                .findFirst().orElseThrow();
        assertThat(hydroCap.qualityHint()).isEqualTo(0.75);
    }

    @Test
    void weightedDispositionDeserialises() {
        var descriptors = DescriptorLoader.load("historical-encounter");
        var leonardo = descriptors.get(0);
        var disp = leonardo.disposition();

        assertThat(disp.socialOrient()).hasSize(2);
        assertThat(disp.socialOrient().get(0).term()).isEqualTo("collaborative");
        assertThat(disp.socialOrient().get(0).weight()).isEqualTo(0.8);
        assertThat(disp.socialOrient().get(1).term()).isEqualTo("independent");
        assertThat(disp.socialOrient().get(1).weight()).isEqualTo(0.2);

        assertThat(disp.ruleFollowing()).hasSize(1);
        assertThat(disp.ruleFollowing().get(0).term()).isEqualTo("flexible");
        assertThat(disp.ruleFollowing().get(0).weight()).isEqualTo(1.0);
    }

    @Test
    void mixedDispositionFormats() {
        var descriptors = DescriptorLoader.load("historical-encounter");
        var nikola = descriptors.get(1);
        var disp = nikola.disposition();

        assertThat(disp.socialOrient()).hasSize(1);
        assertThat(disp.socialOrient().get(0).term()).isEqualTo("independent");

        assertThat(disp.riskAppetite()).hasSize(2);
        assertThat(disp.riskAppetite().get(0).term()).isEqualTo("extreme");
        assertThat(disp.riskAppetite().get(0).weight()).isEqualTo(0.95);

        assertThat(disp.conflictMode()).hasSize(2);
        assertThat(disp.conflictMode().get(0).term()).isEqualTo("competing");
        assertThat(disp.conflictMode().get(0).weight()).isEqualTo(0.7);
    }

    @Test
    void modelAndArchetypeFields() {
        var descriptors = DescriptorLoader.load("historical-encounter");
        var leonardo = descriptors.get(0);

        assertThat(leonardo.archetype()).isEqualTo("polymath");
        assertThat(leonardo.provider()).isEqualTo("anthropic");
        assertThat(leonardo.modelFamily()).isEqualTo("claude");
        assertThat(leonardo.modelVersion()).isEqualTo("opus-5");

        var nikola = descriptors.get(1);
        assertThat(nikola.archetype()).isEqualTo("visionary");

        var historian = descriptors.get(2);
        assertThat(historian.archetype()).isNull();
        assertThat(historian.provider()).isNull();
    }
}
