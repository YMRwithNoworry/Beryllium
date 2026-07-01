package alku.beryllium.verify;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.bridge.NativeStatus;
import alku.beryllium.compute.EntitySectionBatchVerifier;
import alku.beryllium.compute.NativeBatchingVerifier;
import alku.beryllium.compute.TargetingConditionsBatchVerifier;

import java.util.Arrays;

public final class BerylliumNativeRuntimeVerifier {
    private BerylliumNativeRuntimeVerifier() {
    }

    public static void main(String[] args) {
        NativeStatus status = NativeBridge.initialize();
        if (status != NativeStatus.OK) {
            throw new AssertionError("Expected bundled native backend to load, got " + status);
        }

        verifyNativeDistance();
        verifyNativeDoubleDistance();
        verifyNativeNearestIndex();
        verifyNativeNearestIndexExclusive();
        verifyNativeNearestBlockCenterIndex();
        verifyNativeRadiusFilters();
        verifyNativeAabbFilter();
        verifyNativeAabbIntersectionFilter();
        NativeBatchingVerifier.verifyDefaultThreshold();
        EntitySectionBatchVerifier.verifyAcceptIntersecting();
        EntitySectionBatchVerifier.verifyAcceptIntersectingAbort();
        EntitySectionBatchVerifier.verifyTypedAcceptIntersecting();
        EntitySectionBatchVerifier.verifyTypedAcceptIntersectingAbort();
        TargetingConditionsBatchVerifier.verifyFilterCandidatesWithinAabb();
        verifyNativeSort();
    }

    private static void verifyNativeDistance() {
        long[] distances = NativeBridge.computeSquaredDistances(0, 64, 0, new int[] {
            0, 64, 0,
            3, 68, 4,
            -1, 63, -2
        });
        assertArrayEquals(new long[] {0, 41, 6}, distances, "native int squared distance");
    }

    private static void verifyNativeDoubleDistance() {
        double[] distances = NativeBridge.computeSquaredDistances(0.0, 64.0, 0.0, new double[] {
            0.0, 64.0, 0.0,
            3.0, 68.0, 4.0,
            -1.0, 63.0, -2.0
        });
        assertArrayEquals(new double[] {0.0, 41.0, 6.0}, distances, "native double squared distance");
    }

    private static void verifyNativeNearestIndex() {
        int nearest = NativeBridge.findNearestIndex(0.0, 0.0, 0.0, -1.0, new double[] {
            9.0, 0.0, 0.0,
            1.0, 0.0, 0.0
        });
        assertEquals(1, nearest, "native nearest index");
    }

    private static void verifyNativeNearestIndexExclusive() {
        int boundary = NativeBridge.findNearestIndexExclusive(0.0, 0.0, 0.0, 4.0, new double[] {
            2.0, 0.0, 0.0,
            1.0, 0.0, 0.0
        });
        assertEquals(1, boundary, "native exclusive nearest boundary");

        int unbounded = NativeBridge.findNearestIndexExclusive(0.0, 0.0, 0.0, -1.0, new double[] {
            2.0, 0.0, 0.0
        });
        assertEquals(0, unbounded, "native exclusive nearest unbounded");
    }

    private static void verifyNativeNearestBlockCenterIndex() {
        int nearest = NativeBridge.findNearestBlockCenterIndex(0.5, 0.5, 0.5, new int[] {
            1, 0, 0,
            -1, 0, 0,
            0, 1, 0,
            0, 1, 1
        });
        assertEquals(2, nearest, "native nearest block center tie order");
    }

    private static void verifyNativeRadiusFilters() {
        int[] intMatches = NativeBridge.filterWithinRadius(0, 64, 0, 40, new int[] {
            0, 64, 0,
            3, 68, 4,
            -1, 63, -2
        });
        assertArrayEquals(new int[] {0, 2}, intMatches, "native int radius filter");

        int[] doubleMatches = NativeBridge.filterWithinRadius(0.0, 64.0, 0.0, 40.0, new double[] {
            0.0, 64.0, 0.0,
            3.0, 68.0, 4.0,
            -1.0, 63.0, -2.0
        });
        assertArrayEquals(new int[] {0, 2}, doubleMatches, "native double radius filter");
    }

    private static void verifyNativeAabbFilter() {
        int[] matches = NativeBridge.filterWithinAabb(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, new double[] {
            0.0, 0.0, 0.0,
            1.0, 0.0, 0.0,
            0.5, 0.5, 0.5
        });
        assertArrayEquals(new int[] {0, 2}, matches, "native AABB filter");
    }

    private static void verifyNativeAabbIntersectionFilter() {
        int[] matches = NativeBridge.filterIntersectingAabb(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, new double[] {
            0.0, 0.0, 0.0, 1.0, 1.0, 1.0,
            1.0, 0.0, 0.0, 2.0, 1.0, 1.0,
            -1.0, -1.0, -1.0, 0.0, 0.0, 0.0,
            0.5, 0.5, 0.5, 1.5, 1.5, 1.5
        });
        assertArrayEquals(new int[] {0, 3}, matches, "native AABB intersection filter");
    }

    private static void verifyNativeSort() {
        int[] order = NativeBridge.sortByDistance(0, 64, 0, new int[] {
            0, 64, 0,
            3, 68, 4,
            -1, 63, -2
        });
        assertArrayEquals(new int[] {0, 2, 1}, order, "native distance sort");
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
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
}
