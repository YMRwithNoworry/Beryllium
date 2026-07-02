package alku.beryllium.verify;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.bridge.NativeStatus;
import alku.beryllium.compute.BlockDistanceSearchVerifier;
import alku.beryllium.compute.BlockDistanceSortVerifier;
import alku.beryllium.compute.EntityDistanceFilterVerifier;
import alku.beryllium.compute.EntityDistancePredicateSearchVerifier;
import alku.beryllium.compute.EntitySectionBatchVerifier;
import alku.beryllium.compute.EntityDistanceSortVerifier;
import alku.beryllium.compute.EntityVariableRadiusFilterVerifier;
import alku.beryllium.compute.NativeBatchingVerifier;
import alku.beryllium.compute.NearestEntitySearchVerifier;
import alku.beryllium.compute.SupportingBlockSearchVerifier;
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
        verifyNativeAnyWithinRadiusExclusive();
        verifyNativeNearestBlockCenterIndex();
        verifyNativeNearestBlockCornerIndex();
        verifyNativeRadiusFilters();
        verifyNativeAabbFilter();
        verifyNativeAabbIntersectionFilter();
        NativeBatchingVerifier.verifyDefaultThreshold();
        EntitySectionBatchVerifier.verifyAcceptIntersecting();
        EntitySectionBatchVerifier.verifyAcceptIntersectingAbort();
        EntitySectionBatchVerifier.verifyTypedAcceptIntersecting();
        EntitySectionBatchVerifier.verifyTypedAcceptIntersectingAbort();
        EntityDistanceFilterVerifier.verifyFilterWithinExclusiveDistance();
        EntityDistanceFilterVerifier.verifyFilterWithinExclusiveDistanceRejectsBoundary();
        EntityDistanceFilterVerifier.verifyFilterWithinInclusiveDistance();
        EntityDistanceFilterVerifier.verifyFilterWithinInclusiveDistanceAcceptsBoundary();
        EntityDistancePredicateSearchVerifier.verifyFindFirstWithinExclusiveDistancePreservesPredicateOrder();
        EntityDistancePredicateSearchVerifier.verifyFindFirstWithinExclusiveDistanceBatchesLargeLists();
        EntityDistancePredicateSearchVerifier.verifyFindFirstWithinExclusiveDistanceBatchesDistanceGateBeforePosttest();
        NearestEntitySearchVerifier.verifyHasAnyWithinExclusiveDistanceShortCircuits();
        NearestEntitySearchVerifier.verifyHasAnyWithinExclusiveDistanceSkipsDistanceForUnboundedMatch();
        NearestEntitySearchVerifier.verifyHasAnyWithinExclusiveDistanceChecksAllWhenMissing();
        EntityVariableRadiusFilterVerifier.verifyFilterWithinInclusiveDistances();
        EntityVariableRadiusFilterVerifier.verifyFilterWithinInclusiveDistancesPreservesOrder();
        EntityVariableRadiusFilterVerifier.verifyFilterWithinInclusiveDistancesRejectsNegativeRadius();
        EntityDistanceSortVerifier.verifySortByDistance();
        EntityDistanceSortVerifier.verifySortByDistanceTieOrder();
        EntityDistanceSortVerifier.verifyFilterWithinExclusiveDistanceSortedByDistance();
        EntityDistanceSortVerifier.verifyFilterWithinExclusiveDistanceSortedByDistanceTieOrder();
        EntityDistanceSortVerifier.verifyFilterWithinExclusiveDistanceSortedByDistancePostFilterOrder();
        EntityDistanceSortVerifier.verifyFindFirstWithinExclusiveDistanceSortedByDistanceShortCircuitsAfterSort();
        BlockDistanceSortVerifier.verifySortByBlockDistance();
        BlockDistanceSortVerifier.verifySortByBlockDistanceTieOrder();
        BlockDistanceSearchVerifier.verifyFindNearestByBlockDistance();
        BlockDistanceSearchVerifier.verifyFindNearestByBlockDistanceTieOrder();
        SupportingBlockSearchVerifier.verifyFindNearest();
        SupportingBlockSearchVerifier.verifyEmptyCandidates();
        TargetingConditionsBatchVerifier.verifyFilterCandidatesWithinAabb();
        TargetingConditionsBatchVerifier.verifyFilterByConstantDistanceBeforePosttest();
        TargetingConditionsBatchVerifier.verifyFilterByVariableDistanceBeforePosttest();
        TargetingConditionsBatchVerifier.verifyFindNearestAfterVariableDistanceAndPosttest();
        TargetingConditionsBatchVerifier.verifyFindNearestAfterPrecomputedDistanceAcceptsNaNRadius();
        verifyNativeSort();
        verifyNativeBlockSort();
        verifyNativeDoubleSort();
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

    private static void verifyNativeAnyWithinRadiusExclusive() {
        boolean boundary = NativeBridge.hasAnyWithinRadiusExclusive(0.0, 0.0, 0.0, 4.0, new double[] {
            2.0, 0.0, 0.0
        });
        assertEquals(false, boundary, "native exclusive any boundary");

        boolean inner = NativeBridge.hasAnyWithinRadiusExclusive(0.0, 0.0, 0.0, 4.0, new double[] {
            2.0, 0.0, 0.0,
            1.0, 0.0, 0.0
        });
        assertEquals(true, inner, "native exclusive any inner position");
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

    private static void verifyNativeNearestBlockCornerIndex() {
        int nearest = NativeBridge.findNearestBlockCornerIndex(0, 0, 0, new int[] {
            1, 0, 0,
            -1, 0, 0,
            0, 2, 0
        });
        assertEquals(0, nearest, "native nearest block corner tie order");
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

        int[] exclusiveMatches = NativeBridge.filterWithinRadiusExclusive(0.0, 0.0, 0.0, 4.0, new double[] {
            2.0, 0.0, 0.0,
            1.0, 0.0, 0.0
        });
        assertArrayEquals(new int[] {1}, exclusiveMatches, "native exclusive double radius filter");

        int[] sortedExclusiveMatches = NativeBridge.sortWithinRadiusExclusive(0.0, 0.0, 0.0, 4.0, new double[] {
            2.0, 0.0, 0.0,
            1.0, 0.0, 0.0,
            -1.0, 0.0, 0.0
        });
        assertArrayEquals(new int[] {1, 2}, sortedExclusiveMatches, "native sorted exclusive double radius filter");

        int[] variableMatches = NativeBridge.filterWithinRadii(0.0, 0.0, 0.0, new double[] {
            0.0, 8.0, 0.0,
            10.0, 0.0, 0.0,
            12.0, 0.0, 0.0,
            15.1, 0.0, 0.0
        }, new double[] {
            64.0,
            64.0,
            144.0,
            225.0
        });
        assertArrayEquals(new int[] {0, 2}, variableMatches, "native variable radius filter");
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

    private static void verifyNativeDoubleSort() {
        int[] order = NativeBridge.sortByDistance(0.0, 64.0, 0.0, new double[] {
            0.0, 64.0, 0.0,
            3.0, 68.0, 4.0,
            -1.0, 63.0, -2.0
        });
        assertArrayEquals(new int[] {0, 2, 1}, order, "native double distance sort");
    }

    private static void verifyNativeBlockSort() {
        int[] order = NativeBridge.sortByBlockDistance(0, 64, 0, new int[] {
            0, 64, 0,
            3, 68, 4,
            -1, 63, -2
        });
        assertArrayEquals(new int[] {0, 2, 1}, order, "native block distance sort");
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(boolean expected, boolean actual, String label) {
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
