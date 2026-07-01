package alku.beryllium.compute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaComputeKernelsTest {
    @Test
    void squaredDistancesShouldComputePackedBlockPositions() {
        int[] positions = {0, 64, 0, 3, 68, 4, -1, 63, -2};
        long[] result = JavaComputeKernels.squaredDistances(0, 64, 0, positions);
        assertArrayEquals(new long[] {0, 41, 6}, result);
    }

    @Test
    void squaredDistancesShouldRejectUnpackedPositions() {
        assertThrows(IllegalArgumentException.class, () -> JavaComputeKernels.squaredDistances(0, 0, 0, new int[] {1, 2}));
    }

    @Test
    void squaredDistancesShouldComputePackedDoublePositions() {
        double[] positions = {0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0};
        double[] result = JavaComputeKernels.squaredDistances(0.0, 64.0, 0.0, positions);
        assertArrayEquals(new double[] {0.0, 41.0, 6.0}, result);
    }

    @Test
    void squaredDistancesDoubleShouldRejectUnpackedPositions() {
        assertThrows(IllegalArgumentException.class, () -> JavaComputeKernels.squaredDistances(0.0, 0.0, 0.0, new double[] {1.0, 2.0}));
    }
}
