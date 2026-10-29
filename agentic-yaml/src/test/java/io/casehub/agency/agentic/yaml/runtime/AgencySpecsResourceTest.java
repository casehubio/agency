package io.casehub.agency.core.yaml.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgencySpecsResourceTest {

    @Test
    void returnsPreSerializedJson() {
        var json = "{\"patterns\":null,\"cognition\":{\"drive\":{\"changeThreshold\":0.1}},\"promptOptimisation\":null,\"world\":null}";
        var bean = new AgencySpecsBean(json);
        var resource = new AgencySpecsResource(bean);

        assertThat(resource.get()).isEqualTo(json);
    }

    @Test
    void returnsEmptyObjectWhenNoSpecsConfigured() {
        var json = "{\"patterns\":null,\"cognition\":null,\"promptOptimisation\":null,\"world\":null}";
        var bean = new AgencySpecsBean(json);
        var resource = new AgencySpecsResource(bean);

        assertThat(resource.get()).isEqualTo(json);
    }

    @Test
    void beanExposesJsonViaGetter() {
        var json = "{\"patterns\":[]}";
        var bean = new AgencySpecsBean(json);

        assertThat(bean.json()).isEqualTo(json);
    }
}
