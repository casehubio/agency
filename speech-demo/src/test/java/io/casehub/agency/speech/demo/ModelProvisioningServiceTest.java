package io.casehub.agency.speech.demo;

import io.casehub.agency.speech.demo.ModelProvisioningService.ModelStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelProvisioningServiceTest {

    @Test
    void init_setsAllModelsToPending() {
        var service = new ModelProvisioningService() {
            @Override void provisionKokoro(String modelName) {}
            @Override void provisionStreamingStt() {}
        };

        service.init();

        int expectedSize = ModelProvisioningService.KOKORO_MODELS.size() + 1;
        assertThat(service.status()).hasSize(expectedSize);
        assertThat(service.status().values()).allMatch(s -> s == ModelStatus.PENDING);
        assertThat(service.allReady()).isFalse();
    }

    @Test
    void provisionAll_setsAllModelsToReady() {
        var service = new ModelProvisioningService() {
            @Override void provisionKokoro(String modelName) {}
            @Override void provisionStreamingStt() {}
        };

        service.init();
        service.provisionAll();

        assertThat(service.allReady()).isTrue();
        assertThat(service.status().values()).allMatch(s -> s == ModelStatus.READY);
    }

    @Test
    void provisionAll_setsErrorOnFailure() {
        var service = new ModelProvisioningService() {
            @Override void provisionKokoro(String modelName) {
                throw new RuntimeException("download failed");
            }
            @Override void provisionStreamingStt() {}
        };

        service.init();
        service.provisionAll();

        assertThat(service.allReady()).isFalse();
        assertThat(service.status().get("kokoro")).isEqualTo(ModelStatus.ERROR);
        assertThat(service.status().get("streaming-stt")).isEqualTo(ModelStatus.READY);
    }

    @Test
    void statusReturnsDefensiveCopy() {
        var service = new ModelProvisioningService() {
            @Override void provisionKokoro(String modelName) {}
            @Override void provisionStreamingStt() {}
        };

        service.init();
        var status1 = service.status();
        service.provisionAll();
        var status2 = service.status();

        assertThat(status1.values()).allMatch(s -> s == ModelStatus.PENDING);
        assertThat(status2.values()).allMatch(s -> s == ModelStatus.READY);
    }
}
