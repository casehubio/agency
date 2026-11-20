package io.casehub.agency.speech.deployment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechAvatarProcessorTest {

    @Test
    void discoversAvatarYamlFromClasspath() throws Exception {
        var processor = new SpeechAvatarProcessor();
        var buildItem = processor.discoverAvatarConfig();
        assertThat(buildItem.definition()).isNotNull();
        assertThat(buildItem.definition().agent()).isEqualTo("test-avatar");
        assertThat(buildItem.definition().sampleRate()).isEqualTo(16000);
    }

    @Test
    void returnsNullDefinitionWhenNoYaml() {
        var buildItem = new SpeechAvatarConfigBuildItem(null);
        assertThat(buildItem.definition()).isNull();
    }
}
