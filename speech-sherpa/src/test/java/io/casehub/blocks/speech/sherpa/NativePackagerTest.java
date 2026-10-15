package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativePackagerTest {

    @TempDir Path tempDir;

    private final String originalCacheDir = System.getProperty("casehub.speech.cache-dir");
    private final String originalAutoDownload = System.getProperty("casehub.speech.auto-download");
    private final Provisioner.Downloader originalDownloader = Provisioner.downloader;

    @AfterEach
    void restoreProvisioner() {
        restoreProperty("casehub.speech.cache-dir", originalCacheDir);
        restoreProperty("casehub.speech.auto-download", originalAutoDownload);
        Provisioner.downloader = originalDownloader;
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static String foreignPlatform() {
        return SherpaLibrary.platformId().equals("win-x64") ? "linux-x64" : "win-x64";
    }

    @Test
    void downloadsRequestedPlatformEvenWhenHostLibrariesAreCached() throws Exception {
        System.setProperty("casehub.speech.cache-dir", tempDir.resolve("cache").toString());
        System.setProperty("casehub.speech.auto-download", "true");
        Files.createDirectories(Provisioner.nativeCacheDir());
        String platform = foreignPlatform();
        var requestedUrl = new AtomicReference<String>();
        Provisioner.downloader = (url, parentDir) -> {
            requestedUrl.set(url);
            throw new SherpaException("Download intercepted by test");
        };

        assertThatThrownBy(() -> NativePackager.packageNative(new String[]{platform}, tempDir.resolve("output")))
                .isInstanceOf(SherpaException.class)
                .hasMessage("Download intercepted by test");
        assertThat(requestedUrl.get()).isEqualTo(Provisioner.nativeLibUrl(platform));
    }

    @Test
    void packagesCachedForeignPlatformWithoutDownloading() throws Exception {
        System.setProperty("casehub.speech.cache-dir", tempDir.resolve("cache").toString());
        String platform = foreignPlatform();
        Path cache = Files.createDirectories(Provisioner.nativeCacheDir(platform));
        String sherpa = NativePackager.sherpaLibNameFor(platform);
        String onnx = NativePackager.onnxRuntimeLibNameFor(platform);
        Files.writeString(cache.resolve(sherpa), "sherpa fixture");
        Files.writeString(cache.resolve(onnx), "onnx fixture");
        Provisioner.downloader = (url, parentDir) -> {
            throw new AssertionError("Cached libraries must not be downloaded");
        };

        Path output = tempDir.resolve("output");
        NativePackager.packageNative(new String[]{platform}, output);

        Path packaged = output.resolve("META-INF/native/sherpa-onnx")
                .resolve(SherpaLibrary.VERSION).resolve(platform);
        assertThat(Files.readString(packaged.resolve(sherpa))).isEqualTo("sherpa fixture");
        assertThat(Files.readString(packaged.resolve(onnx))).isEqualTo("onnx fixture");
    }

    @Test
    void rejectsNoArguments() {
        assertThatThrownBy(() -> NativePackager.packageNative(new String[]{}, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform ID");
    }

    @Test
    void rejectsUnknownPlatform() {
        assertThatThrownBy(() -> NativePackager.packageNative(new String[]{"unknown-arch"}, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown-arch");
    }
}
