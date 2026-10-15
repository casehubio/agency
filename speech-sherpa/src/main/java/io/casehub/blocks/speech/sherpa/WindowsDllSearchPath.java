package io.casehub.blocks.speech.sherpa;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;

final class WindowsDllSearchPath {

    private WindowsDllSearchPath() {}

    static void configure(Path directory) {
        if (directory == null || !isWindows()) { return; }

        Linker linker = Linker.nativeLinker();
        MemorySegment symbol = linker.defaultLookup().find("SetDllDirectoryW")
                .orElseThrow(() -> new SherpaException("SetDllDirectoryW is unavailable"));
        MethodHandle setDllDirectory = linker.downcallHandle(symbol,
                FunctionDescriptor.of(JAVA_INT, ADDRESS));

        char[] path = (directory.toAbsolutePath() + "\0").toCharArray();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativePath = arena.allocateFrom(JAVA_CHAR, path);
            int result = (int) setDllDirectory.invokeExact(nativePath);
            if (result == 0) {
                throw new SherpaException("Failed to configure Windows DLL directory: " + directory);
            }
        } catch (SherpaException e) {
            throw e;
        } catch (Throwable e) {
            throw new SherpaException("Failed to configure Windows DLL directory: " + directory, e);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
