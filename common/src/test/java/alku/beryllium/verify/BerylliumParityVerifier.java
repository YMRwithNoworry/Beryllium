package alku.beryllium.verify;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.bridge.NativeStatus;
import alku.beryllium.compute.JavaComputeKernels;

import java.util.Arrays;

public final class BerylliumParityVerifier {
    private BerylliumParityVerifier() {
    }

    public static void main(String[] args) {
        verifyJavaKernel();
        verifyJavaKernelDouble();
        verifyJavaNearestIndex();
        verifyJavaFilterAndSort();
        verifyJavaDoubleFilter();
        verifyJavaAabbFilter();
        verifyJavaIntegerOverflowSemantics();
        verifyNativeBridgeFallback();
        verifyNativeBridgeFallbackDouble();
        verifyNativeBridgeNearestIndex();
        verifyNativeBridgeFilterAndSort();
        verifyNativeBridgeDoubleFilter();
        verifyNativeBridgeAabbFilter();
        verifyNativeBridgeLargeFilterAndSort();
        verifyNativeBridgeIntegerOverflowSemantics();
        verifyInvalidInput();
    }

    private static void verifyJavaKernel() {
        int[] positions = {0, 64, 0, 3, 68, 4, -1, 63, -2};
        long[] expected = {0, 41, 6};
        long[] actual = JavaComputeKernels.squaredDistances(0, 64, 0, positions);
        assertArrayEquals(expected, actual, "Java reference kernel");
    }

    private static void verifyNativeBridgeFallback() {
        NativeStatus status = NativeBridge.initialize();
        if (status != NativeStatus.OK && status != NativeStatus.UNAVAILABLE) {
            throw new AssertionError("Unexpected native initialization status: " + status);
        }

        int[] positions = {0, 64, 0, 3, 68, 4, -1, 63, -2};
        long[] expected = {0, 41, 6};
        long[] actual = NativeBridge.computeSquaredDistances(0, 64, 0, positions);
        assertArrayEquals(expected, actual, "Native bridge fallback/parity");
    }

    private static void verifyJavaKernelDouble() {
        double[] positions = {0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0};
        double[] expected = {0.0, 41.0, 6.0};
        double[] actual = JavaComputeKernels.squaredDistances(0.0, 64.0, 0.0, positions);
        assertArrayEquals(expected, actual, "Java double reference kernel");
    }

    private static void verifyNativeBridgeFallbackDouble() {
        double[] positions = {0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0};
        double[] expected = {0.0, 41.0, 6.0};
        double[] actual = NativeBridge.computeSquaredDistances(0.0, 64.0, 0.0, positions);
        assertArrayEquals(expected, actual, "Native bridge double fallback/parity");
    }

    private static void verifyNativeBridgeNearestIndex() {
        double[] positions = descendingAxisPositionsDouble(5000);
        int nearest = NativeBridge.findNearestIndex(0.0, 0.0, 0.0, 1024.0, positions);
        int missing = NativeBridge.findNearestIndex(0.0, 0.0, 0.0, 4.0, new double[] {3.0, 0.0, 0.0});

        if (nearest != 4999) {
            throw new AssertionError("Native bridge nearest index mismatch, expected 4999 but got " + nearest);
        }
        if (missing != -1) {
            throw new AssertionError("Native bridge nearest index radius rejection mismatch, expected -1 but got " + missing);
        }
    }

    private static void verifyNativeBridgeFilterAndSort() {
        int[] positions = {0, 64, 0, 3, 68, 4, -1, 63, -2};
        int[] filtered = NativeBridge.filterWithinRadius(0, 64, 0, 40, positions);
        int[] sorted = NativeBridge.sortByDistance(0, 64, 0, positions);
        assertArrayEquals(new int[] {0, 2}, filtered, "Native bridge radius filter");
        assertArrayEquals(new int[] {0, 2, 1}, sorted, "Native bridge distance sort");
    }

    private static void verifyNativeBridgeDoubleFilter() {
        double[] positions = descendingAxisPositionsDouble(5000);
        int[] filtered = NativeBridge.filterWithinRadius(0.0, 0.0, 0.0, 1024.0, positions);
        assertArrayEquals(range(4967, 5000), filtered, "Native bridge large double radius filter");
    }

    private static void verifyNativeBridgeAabbFilter() {
        double[] edgePositions = {
            0.0, 0.0, 0.0,
            1.0, 0.0, 0.0,
            -1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
        };
        int[] edgeFiltered = NativeBridge.filterWithinAabb(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, edgePositions);
        assertArrayEquals(new int[] {0}, edgeFiltered, "Native bridge AABB edge semantics");

        double[] positions = descendingAxisPositionsDouble(5000);
        int[] filtered = NativeBridge.filterWithinAabb(0.0, -1.0, -1.0, 33.0, 1.0, 1.0, positions);
        assertArrayEquals(range(4967, 5000), filtered, "Native bridge large AABB filter");
    }

    private static void verifyNativeBridgeLargeFilterAndSort() {
        int[] positions = descendingAxisPositions(5000);
        int[] filtered = NativeBridge.filterWithinRadius(0, 0, 0, 1024, positions);
        int[] sorted = NativeBridge.sortByDistance(0, 0, 0, positions);

        assertArrayEquals(range(4967, 5000), filtered, "Native bridge large radius filter");
        assertArrayEquals(descendingRange(4999, 0), sorted, "Native bridge large distance sort");
    }

    private static void verifyNativeBridgeIntegerOverflowSemantics() {
        int[] positions = {Integer.MAX_VALUE, 0, 0};
        long[] actual = NativeBridge.computeSquaredDistances(Integer.MIN_VALUE, 0, 0, positions);
        assertArrayEquals(new long[] {-8_589_934_591L}, actual, "Native bridge integer overflow parity");
    }

    private static void verifyJavaFilterAndSort() {
        int[] positions = {0, 64, 0, 3, 68, 4, -1, 63, -2};
        int[] filtered = JavaComputeKernels.filterWithinRadius(0, 64, 0, 40, positions);
        int[] sorted = JavaComputeKernels.sortByDistance(0, 64, 0, positions);
        assertArrayEquals(new int[] {0, 2}, filtered, "Java radius filter");
        assertArrayEquals(new int[] {0, 2, 1}, sorted, "Java distance sort");
    }

    private static void verifyJavaDoubleFilter() {
        double[] positions = descendingAxisPositionsDouble(5000);
        int[] filtered = JavaComputeKernels.filterWithinRadius(0.0, 0.0, 0.0, 1024.0, positions);
        assertArrayEquals(range(4967, 5000), filtered, "Java large double radius filter");
    }

    private static void verifyJavaAabbFilter() {
        double[] edgePositions = {
            0.0, 0.0, 0.0,
            1.0, 0.0, 0.0,
            -1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
        };
        int[] edgeFiltered = JavaComputeKernels.filterWithinAabb(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, edgePositions);
        assertArrayEquals(new int[] {0}, edgeFiltered, "Java AABB edge semantics");

        double[] positions = descendingAxisPositionsDouble(5000);
        int[] filtered = JavaComputeKernels.filterWithinAabb(0.0, -1.0, -1.0, 33.0, 1.0, 1.0, positions);
        assertArrayEquals(range(4967, 5000), filtered, "Java large AABB filter");
    }

    private static void verifyJavaNearestIndex() {
        double[] positions = descendingAxisPositionsDouble(5000);
        int nearest = JavaComputeKernels.findNearestIndex(0.0, 0.0, 0.0, 1024.0, positions);
        int missing = JavaComputeKernels.findNearestIndex(0.0, 0.0, 0.0, 4.0, new double[] {3.0, 0.0, 0.0});

        if (nearest != 4999) {
            throw new AssertionError("Java nearest index mismatch, expected 4999 but got " + nearest);
        }
        if (missing != -1) {
            throw new AssertionError("Java nearest index radius rejection mismatch, expected -1 but got " + missing);
        }
    }

    private static void verifyJavaIntegerOverflowSemantics() {
        int[] positions = {Integer.MAX_VALUE, 0, 0};
        long[] actual = JavaComputeKernels.squaredDistances(Integer.MIN_VALUE, 0, 0, positions);
        assertArrayEquals(new long[] {-8_589_934_591L}, actual, "Java integer overflow semantics");
    }

    private static void verifyInvalidInput() {
        try {
            JavaComputeKernels.squaredDistances(0, 0, 0, new int[] {1, 2});
        } catch (IllegalArgumentException expected) {
            return;
        }

        throw new AssertionError("Expected invalid packed positions to be rejected");
    }

    private static void assertArrayEquals(long[] expected, long[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + " mismatch, expected " + Arrays.toString(expected) + " but got " + Arrays.toString(actual));
        }
    }

    private static void assertArrayEquals(int[] expected, int[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + " mismatch, expected " + Arrays.toString(expected) + " but got " + Arrays.toString(actual));
        }
    }

    private static void assertArrayEquals(double[] expected, double[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + " mismatch, expected " + Arrays.toString(expected) + " but got " + Arrays.toString(actual));
        }
    }

    private static int[] descendingAxisPositions(int count) {
        int[] positions = new int[count * 3];
        for (int index = 0; index < count; index++) {
            positions[index * 3] = count - 1 - index;
        }
        return positions;
    }

    private static double[] descendingAxisPositionsDouble(int count) {
        double[] positions = new double[count * 3];
        for (int index = 0; index < count; index++) {
            positions[index * 3] = count - 1 - index;
        }
        return positions;
    }

    private static int[] range(int startInclusive, int endExclusive) {
        int[] result = new int[endExclusive - startInclusive];
        for (int index = 0; index < result.length; index++) {
            result[index] = startInclusive + index;
        }
        return result;
    }

    private static int[] descendingRange(int startInclusive, int endInclusive) {
        int[] result = new int[startInclusive - endInclusive + 1];
        for (int index = 0; index < result.length; index++) {
            result[index] = startInclusive - index;
        }
        return result;
    }
}
