package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.Affect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoodMapperTest {

    @Test
    void neutralInDeadZone() {
        var mapper = new MoodMapper();
        assertThat(mapper.map(new Affect(0.1, 0.1, 0.1), 0)).isNull();
    }

    @Test
    void classifiesMehrabianOctants() {
        assertThat(MoodMapper.classify(new Affect(0.5, 0.5, 0.5))).isEqualTo("exuberant");
        assertThat(MoodMapper.classify(new Affect(0.5, 0.5, -0.5))).isEqualTo("dependent");
        assertThat(MoodMapper.classify(new Affect(0.5, -0.5, 0.5))).isEqualTo("relaxed");
        assertThat(MoodMapper.classify(new Affect(0.5, -0.5, -0.5))).isEqualTo("docile");
        assertThat(MoodMapper.classify(new Affect(-0.5, 0.5, 0.5))).isEqualTo("hostile");
        assertThat(MoodMapper.classify(new Affect(-0.5, 0.5, -0.5))).isEqualTo("anxious");
        assertThat(MoodMapper.classify(new Affect(-0.5, -0.5, 0.5))).isEqualTo("disdainful");
        assertThat(MoodMapper.classify(new Affect(-0.5, -0.5, -0.5))).isEqualTo("bored");
    }

    @Test
    void hysteresisPreventsBouncing() {
        var mapper = new MoodMapper();
        assertThat(mapper.map(new Affect(0.6, 0.6, 0.6), 0)).isEqualTo("exuberant");
        assertThat(mapper.map(new Affect(-0.3, 0.3, 0.3), 100)).isEqualTo("exuberant");
    }

    @Test
    void dwellTimeEnforcedBeforeTransition() {
        var mapper = new MoodMapper();
        mapper.map(new Affect(0.6, 0.6, 0.6), 0);
        assertThat(mapper.map(new Affect(-0.6, 0.6, 0.6), 1000)).isEqualTo("exuberant");
        assertThat(mapper.map(new Affect(-0.6, 0.6, 0.6), 6000)).isEqualTo("hostile");
    }

    @Test
    void transitionAfterDwell() {
        var mapper = new MoodMapper();
        mapper.map(new Affect(0.6, 0.6, 0.6), 0);
        assertThat(mapper.map(new Affect(-0.6, -0.6, -0.6), 10000)).isEqualTo("bored");
    }

    @Test
    void dwellTimeEnforcedOnNullTransition() {
        var mapper = new MoodMapper();
        mapper.map(new Affect(0.6, 0.6, 0.6), 0);
        assertThat(mapper.map(new Affect(0.1, 0.1, 0.1), 1000)).isEqualTo("exuberant");
        assertThat(mapper.map(new Affect(0.1, 0.1, 0.1), 4000)).isEqualTo("exuberant");
        assertThat(mapper.map(new Affect(0.1, 0.1, 0.1), 6000)).isNull();
    }

    @Test
    void deadZoneDwellResetsOnReEntry() {
        var mapper = new MoodMapper();
        mapper.map(new Affect(0.6, 0.6, 0.6), 0);
        mapper.map(new Affect(0.1, 0.1, 0.1), 1000);
        mapper.map(new Affect(0.6, 0.6, 0.6), 3000);
        mapper.map(new Affect(0.1, 0.1, 0.1), 4000);
        assertThat(mapper.map(new Affect(0.1, 0.1, 0.1), 8000)).isEqualTo("exuberant");
        assertThat(mapper.map(new Affect(0.1, 0.1, 0.1), 10000)).isNull();
    }

    @Test
    void noAdoptedMoodDeadZoneReturnsNullImmediately() {
        var mapper = new MoodMapper();
        assertThat(mapper.map(new Affect(0.1, 0.1, 0.1), 0)).isNull();
        assertThat(mapper.map(new Affect(0.1, 0.1, 0.1), 100)).isNull();
    }
}
