package alku.beryllium.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

final class NativeLibraryLoader {
    private static final String LIBRARY_NAME = "beryllium_native";
    private static final String LIBRARY_PATH_PROPERTY = "beryllium.native.path";
    private static final String RESOURCE_ROOT = "/assets/beryllium/native/";

    private NativeLibraryLoader() {
    }

    static boolean tryLoad() {
        String explicitPath = System.getProperty(LIBRARY_PATH_PROPERTY);
        if (explicitPath != null && !explicitPath.isBlank()) {
            return loadPath(explicitPath);
        }

        return loadBundled() || loadLibraryName();
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

    private static boolean loadBundled() {
        String resourcePath = RESOURCE_ROOT + osName() + "/" + archName() + "/" + System.mapLibraryName(LIBRARY_NAME);
        try (InputStream input = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return false;
            }

            Path extracted = Files.createTempFile("beryllium-native-", "-" + System.mapLibraryName(LIBRARY_NAME));
            extracted.toFile().deleteOnExit();
            Files.copy(input, extracted, StandardCopyOption.REPLACE_EXISTING);
            System.load(extracted.toAbsolutePath().toString());
            return true;
        } catch (IOException | UnsatisfiedLinkError | SecurityException ignored) {
            return false;
        }
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
