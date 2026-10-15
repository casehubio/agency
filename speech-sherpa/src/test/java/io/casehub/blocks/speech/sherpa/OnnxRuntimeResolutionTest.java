package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OnnxRuntimeResolutionTest {

    @Test
    void provisionedRuntimeTakesItsPathFromNativeDirectory(@TempDir Path nativeDir) throws Exception {
        String original = System.getProperty("sherpa.native.dir");
        Path expected = nativeDir.resolve(SherpaLibrary.onnxRuntimeLibName());
        Files.createFile(expected);
        try {
            System.setProperty("sherpa.native.dir", nativeDir.toString());
            assertThat(OnnxRuntimeLibrary.provisionedLibraryPath()).isEqualTo(expected);
        } finally {
            if (original == null) {
                System.clearProperty("sherpa.native.dir");
            } else {
                System.setProperty("sherpa.native.dir", original);
            }
        }
    }
}
