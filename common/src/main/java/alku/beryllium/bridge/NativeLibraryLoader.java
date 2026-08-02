package alku.beryllium.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

final class NativeLibraryLoader {
    private static final String LIBRARY_NAME = "beryllium_native";
    private static final String CUBECL_LIBRARY_NAME = "beryllium_cubecl";
    private static final String LIBRARY_PATH_PROPERTY = "beryllium.native.path";
    private static final String CUBECL_LIBRARY_PATH_PROPERTY = "beryllium.native.cubecl.path";
    private static final String RESOURCE_ROOT = "/assets/beryllium/native/";
    private static int cubeclLoadState;

    private NativeLibraryLoader() {
    }

    static boolean tryLoad() {
        String explicitPath = System.getProperty(LIBRARY_PATH_PROPERTY);
        if (explicitPath != null && !explicitPath.isBlank()) {
            return loadPath(explicitPath);
        }

        return loadBundled(LIBRARY_NAME, false) || loadLibraryName();
    }

    static synchronized boolean tryLoadCubeclPreview() {
        if (cubeclLoadState != 0) {
            return cubeclLoadState > 0;
        }

        String explicitPath = System.getProperty(CUBECL_LIBRARY_PATH_PROPERTY);
        boolean loaded = explicitPath != null && !explicitPath.isBlank()
            ? loadCubeclPath(explicitPath)
            : loadBundled(CUBECL_LIBRARY_NAME, true);
        cubeclLoadState = loaded ? 1 : -1;
        return loaded;
    }

    static synchronized boolean isCubeclPreviewLoaded() {
        return cubeclLoadState > 0;
    }

    static boolean hasCubeclPreviewCandidate() {
        String explicitPath = System.getProperty(CUBECL_LIBRARY_PATH_PROPERTY);
        if (explicitPath != null && !explicitPath.isBlank()) {
            return true;
        }
        return NativeLibraryLoader.class.getResource(resourcePath(CUBECL_LIBRARY_NAME)) != null;
    }

    private static boolean loadPath(String path) {
        try {
            System.load(path);
            return true;
        } catch (UnsatisfiedLinkError | SecurityException ignored) {
            return false;
        }
    }

    private static boolean loadLibraryName() {
        try {
            System.loadLibrary(LIBRARY_NAME);
            return true;
        } catch (UnsatisfiedLinkError | SecurityException ignored) {
            return false;
        }
    }

    private static boolean loadCubeclPath(String path) {
        try {
            Path source = Path.of(path);
            Path directory = Files.createTempDirectory("beryllium-cubecl-");
            Path extracted = directory.resolve(System.mapLibraryName(CUBECL_LIBRARY_NAME));
            directory.toFile().deleteOnExit();
            extracted.toFile().deleteOnExit();
            Files.copy(source, extracted, StandardCopyOption.REPLACE_EXISTING);
            System.load(extracted.toAbsolutePath().toString());
            return true;
        } catch (IOException | UnsatisfiedLinkError | SecurityException ignored) {
            return false;
        }
    }

    private static boolean loadBundled(String libraryName, boolean preserveFileName) {
        String resourcePath = resourcePath(libraryName);
        try (InputStream input = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return false;
            }

            Path extracted;
            if (preserveFileName) {
                Path directory = Files.createTempDirectory("beryllium-cubecl-");
                directory.toFile().deleteOnExit();
                extracted = directory.resolve(System.mapLibraryName(libraryName));
            } else {
                extracted = Files.createTempFile("beryllium-native-", "-" + System.mapLibraryName(libraryName));
            }
            extracted.toFile().deleteOnExit();
            Files.copy(input, extracted, StandardCopyOption.REPLACE_EXISTING);
            System.load(extracted.toAbsolutePath().toString());
            return true;
        } catch (IOException | UnsatisfiedLinkError | SecurityException ignored) {
            return false;
        }
    }

    private static String resourcePath(String libraryName) {
        return RESOURCE_ROOT + osName() + "/" + archName() + "/" + System.mapLibraryName(libraryName);
    }

    private static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos";
        }
        return "linux";
    }

    private static String archName() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> arch;
        };
    }
}
