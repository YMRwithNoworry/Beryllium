package alku.beryllium.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeBridgeTest {
    @Test
    void initializeShouldReturnUnavailableWhenNativeLibraryIsMissing() {
        assertEquals(NativeStatus.UNAVAILABLE, NativeBridge.initialize());
        assertFalse(NativeBridge.isLoaded());
    }

    @Test
    void computeSquaredDistancesShouldFallBackToJavaWhenNativeLibraryIsMissing() {
        int[] positions = {0, 64, 0, 3, 68, 4, -1, 63, -2};
        long[] result = NativeBridge.computeSquaredDistances(0, 64, 0, positions);
        assertArrayEquals(new long[] {0, 41, 6}, result);
    }

    @Test
    void computeSquaredDistancesShouldRejectUnpackedPositions() {
        assertThrows(IllegalArgumentException.class, () -> NativeBridge.computeSquaredDistances(0, 0, 0, new int[] {1, 2}));
    }

    @Test
    void computeSquaredDistancesDoubleShouldFallBackToJavaWhenNativeLibraryIsMissing() {
        double[] positions = {0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0};
        double[] result = NativeBridge.computeSquaredDistances(0.0, 64.0, 0.0, positions);
        assertArrayEquals(new double[] {0.0, 41.0, 6.0}, result);
    }

    @Test
    void computeSquaredDistancesDoubleShouldRejectUnpackedPositions() {
        assertThrows(IllegalArgumentException.class, () -> NativeBridge.computeSquaredDistances(0.0, 0.0, 0.0, new double[] {1.0, 2.0}));
    }
}
