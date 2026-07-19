package alku.beryllium.verify;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.bridge.NativeStatus;
import alku.beryllium.compute.BlockDistanceSearchVerifier;
import alku.beryllium.compute.BlockDistanceSortVerifier;
import alku.beryllium.compute.ChunkDistanceSearchVerifier;
import alku.beryllium.compute.ChunkSendBatchSelectorVerifier;
import alku.beryllium.compute.EntityDistanceFilterVerifier;
import alku.beryllium.compute.EntityDistancePredicateSearchVerifier;
import alku.beryllium.compute.EntitySectionBatchVerifier;
import alku.beryllium.compute.EntityDistanceSortVerifier;
import alku.beryllium.compute.EntityVariableRadiusFilterVerifier;
import alku.beryllium.compute.JavaComputeKernels;
import alku.beryllium.compute.NativeBatchingVerifier;
import alku.beryllium.compute.NearestEntitySearchVerifier;
import alku.beryllium.compute.PotentialEnergyBatchVerifier;
import alku.beryllium.compute.PrioritizedEntitySearchVerifier;
import alku.beryllium.compute.SupportingBlockSearchVerifier;
import alku.beryllium.compute.TargetingConditionsBatchVerifier;

import java.util.Arrays;

public final class BerylliumParityVerifier {
    private BerylliumParityVerifier() {
    }

    public static void main(String[] args) {
        ChunkSendBatchSelectorVerifier.verifyJavaKernelMatchesGuavaTopK();
        ChunkSendBatchSelectorVerifier.verifyJavaKernelLimitBoundaries();
        ChunkSendBatchSelectorVerifier.verifyJavaKernelPreservesWrappingIntDistance();
        ChunkSendBatchSelectorVerifier.verifyJavaKernelMatchesGuavaTieBehavior();
        ChunkSendBatchSelectorVerifier.verifyJavaKernelLeavesOutputTailUntouched();
        ChunkSendBatchSelectorVerifier.verifyJavaKernelLargeBatchMatchesGuava();
        ChunkSendBatchSelectorVerifier.verifyBridgeMatchesGuavaTopK();
        ChunkSendBatchSelectorVerifier.verifyBridgeLeavesOutputTailUntouched();
        ChunkSendBatchSelectorVerifier.verifySelectorFacadeMatchesGuavaTopK();
        ChunkSendBatchSelectorVerifier.verifyFastutilPrimitiveStreamPreservesBoxedStreamOrder();
        verifyJavaKernel();
        verifyJavaKernelDouble();
        verifyJavaNearestIndex();
        verifyJavaNearestIndexTieAndUnbounded();
        verifyJavaNearestIndexExclusive();
        verifyJavaAnyWithinRadiusExclusive();
        verifyJavaNearestBlockCenterIndex();
        verifyJavaNearestBlockCenterIndexRejectsNullPositions();
        verifyJavaNearestBlockCornerIndex();
        verifyJavaFilterAndSort();
        verifyJavaBlockSort();
        verifyJavaDoubleSort();
        verifyJavaDoubleFilter();
        verifyJavaDoubleFusedSortAndRadiusPrefix();
        verifyJavaNearestSelection();
        verifyJavaAabbFilter();
        verifyJavaAabbIntersectionFilter();
        PotentialEnergyBatchVerifier.verifyPotentialEnergyChangeMatchesVanillaPointChargeMath();
        PotentialEnergyBatchVerifier.verifyPotentialEnergyChangeReturnsInfinityAtSamePosition();
        PotentialEnergyBatchVerifier.verifyPotentialEnergyChangePreservesNegativeZeroMultiplierResult();
        PotentialEnergyBatchVerifier.verifyPotentialEnergyBatchSkipsChargeAccessForZeroMultiplier();
        PotentialEnergyBatchVerifier.verifyPotentialEnergyBatchPreservesExtractionOrder();
        PotentialEnergyBatchVerifier.verifyPotentialEnergyBatchJavaPathPreservesSequentialAccumulation();
        verifyJavaIntegerOverflowSemantics();
        verifyNativeBridgeFallback();
        verifyNativeBridgeFallbackDouble();
        verifyNativeBridgeNearestIndex();
        verifyNativeBridgeNearestIndexTieAndUnbounded();
        verifyNativeBridgeNearestIndexExclusive();
        verifyNativeBridgeAnyWithinRadiusExclusive();
        verifyNativeBridgeNearestBlockCenterIndex();
        verifyNativeBridgeNearestBlockCornerIndex();
        verifyNativeBridgeFilterAndSort();
        verifyNativeBridgeBlockSort();
        verifyNativeBridgeDoubleSort();
        verifyNativeBridgeDoubleFusedSortAndRadiusPrefix();
        verifyNativeBridgeNearestSelection();
        verifyNativeBridgeDoubleFilter();
        verifyNativeBridgeVariableRadiusFilter();
        verifyNativeBridgeAabbFilter();
        verifyNativeBridgeAabbIntersectionFilter();
        verifyNativeBridgePotentialEnergyChange();
        verifyNativeBridgeLargeFilterAndSort();
        verifyNativeBridgeLargeBlockSort();
        verifyNativeBridgeIntegerOverflowSemantics();
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
        EntityDistancePredicateSearchVerifier.verifyFindFirstWithinInclusiveDistanceAcceptsBoundaryAndShortCircuits();
        EntityDistancePredicateSearchVerifier.verifyFindFirstWithinInclusiveDistanceBatchesDistanceGateBeforePosttest();
        EntityDistancePredicateSearchVerifier.verifyFindFirstWithinInclusiveDistanceAfterDistanceBatchesDistanceGateFirst();
        EntityDistancePredicateSearchVerifier.verifyFindFirstWithinExclusiveDistanceAfterDistanceBatchesDistanceGateFirst();
        NearestEntitySearchVerifier.verifyHasAnyWithinExclusiveDistanceShortCircuits();
        NearestEntitySearchVerifier.verifyHasAnyWithinExclusiveDistanceSkipsDistanceForUnboundedMatch();
        NearestEntitySearchVerifier.verifyHasAnyWithinExclusiveDistanceChecksAllWhenMissing();
        NearestEntitySearchVerifier.verifyHasAnyWithinExclusiveDistanceBatchesPredicateBeforeDistance();
        PrioritizedEntitySearchVerifier.verifyFindFirstWithFallbackPrefersFirstPass();
        PrioritizedEntitySearchVerifier.verifyFindFirstWithFallbackRescansForFallback();
        EntityVariableRadiusFilterVerifier.verifyFilterWithinInclusiveDistances();
        EntityVariableRadiusFilterVerifier.verifyFilterWithinInclusiveDistancesPreservesOrder();
        EntityVariableRadiusFilterVerifier.verifyFilterWithinInclusiveDistancesRejectsNegativeRadius();
        EntityVariableRadiusFilterVerifier.verifyFindFirstWithinInclusiveDistancesAfterDistanceShortCircuitsPosttest();
        EntityVariableRadiusFilterVerifier.verifyFindFirstWithinInclusiveDistancesAfterDistanceBatchesDistanceGateFirst();
        EntityDistanceSortVerifier.verifySortByDistance();
        EntityDistanceSortVerifier.verifySortByDistanceTieOrder();
        EntityDistanceSortVerifier.verifyFilterWithinExclusiveDistanceSortedByDistance();
        EntityDistanceSortVerifier.verifyFilterWithinExclusiveDistanceSortedByDistanceTieOrder();
        EntityDistanceSortVerifier.verifyFilterWithinExclusiveDistanceSortedByDistancePostFilterOrder();
        EntityDistanceSortVerifier.verifyFindFirstWithinExclusiveDistanceSortedByDistanceShortCircuitsAfterSort();
        EntityDistanceSortVerifier.verifyFindFirstWithinExclusiveDistanceAfterPredicatesSortedByDistancePreservesFilterOrder();
        EntityDistanceSortVerifier.verifyFindFirstWithinExclusiveDistanceAfterPredicatesSortedByDistanceTieOrder();
        EntityDistanceSortVerifier.verifyFindFirstSortedByDistanceShortCircuitsAfterSort();
        EntityDistanceSortVerifier.verifyFindFirstSortedByDistancePreservesTieOrder();
        EntityDistanceSortVerifier.verifyFindFirstSortedWithinExclusiveDistanceEvaluatesPredicateBeforeRadius();
        EntityDistanceSortVerifier.verifyFindFirstSortedWithinExclusiveDistanceShortCircuitsAfterRadius();
        EntityDistanceSortVerifier.verifyNearestItemTopKHitPreservesPredicateOrder();
        EntityDistanceSortVerifier.verifyNearestItemTopKFallbackPreservesPredicateOrder();
        EntityDistanceSortVerifier.verifyFindFirstBySortedOrderWithinPrefixPreservesPredicateOrderAndShortCircuits();
        BlockDistanceSortVerifier.verifySortByBlockDistance();
        BlockDistanceSortVerifier.verifySortByBlockDistanceTieOrder();
        BlockDistanceSortVerifier.verifyFindFirstSortedByBlockDistancePostFilterOrder();
        BlockDistanceSortVerifier.verifyFindFirstSortedByBlockDistanceNoMatchChecksAllSortedCandidates();
        BlockDistanceSearchVerifier.verifyFindNearestByBlockDistance();
        BlockDistanceSearchVerifier.verifyFindNearestByBlockDistanceTieOrder();
        BlockDistanceSearchVerifier.verifyFindNearestPositionByBlockDistancePreservesPredicateOrder();
        BlockDistanceSearchVerifier.verifyFindNearestPositionByBlockDistanceTieOrder();
        BlockDistanceSearchVerifier.verifyFindNearestWithinInclusiveBlockDistanceFiltersBeforePostPredicate();
        BlockDistanceSearchVerifier.verifyFindNearestWithinInclusiveBlockDistanceBatchesRadiusBeforePostPredicate();
        BlockDistanceSearchVerifier.verifyFindNearestWithinInclusiveBlockDistanceBatchesDirectNearest();
        BlockDistanceSearchVerifier.verifyFindNearestPositionWithinInclusiveBlockDistanceTieOrder();
        BlockDistanceSearchVerifier.verifyFilterWithinInclusiveBlockDistancePreservesEncounterOrderAfterRadius();
        BlockDistanceSearchVerifier.verifyFilterWithinInclusiveBlockDistanceBatchesRadiusBeforePredicate();
        BlockDistanceSearchVerifier.verifyCountWithinInclusiveBlockDistanceAcceptsBoundary();
        BlockDistanceSearchVerifier.verifyCountWithinInclusiveBlockDistanceBatchesRadius();
        BlockDistanceSearchVerifier.verifyFindFirstWithinInclusiveBlockDistanceFiltersBeforePredicate();
        BlockDistanceSearchVerifier.verifyFindFirstWithinInclusiveBlockDistanceBatchesRadiusBeforePredicate();
        SupportingBlockSearchVerifier.verifyFindNearest();
        SupportingBlockSearchVerifier.verifyEmptyCandidates();
        SupportingBlockSearchVerifier.verifyFindNearestIgnoresUnusedPackedCapacity();
        TargetingConditionsBatchVerifier.verifyFilterCandidatesWithinAabb();
        TargetingConditionsBatchVerifier.verifyFilterByConstantDistanceBeforePosttest();
        TargetingConditionsBatchVerifier.verifyFilterByVariableDistanceBeforePosttest();
        TargetingConditionsBatchVerifier.verifyFindNearestAfterVariableDistanceAndPosttest();
        TargetingConditionsBatchVerifier.verifyFindNearestAfterConstantDistanceUsesSeparateDistanceOrigin();
        TargetingConditionsBatchVerifier.verifyFindNearestAfterVariableDistanceUsesSeparateDistanceOrigin();
        TargetingConditionsBatchVerifier.verifyFindNearestAfterPrecomputedDistanceUsesSeparateDistanceOrigin();
        TargetingConditionsBatchVerifier.verifyFindNearestAfterPrecomputedDistanceAcceptsNaNRadius();
        ChunkDistanceSearchVerifier.verifyExclusiveBoundaryAndEncounterOrder();
        ChunkDistanceSearchVerifier.verifyEligibilityRunsBeforeCoordinateAccess();
        ChunkDistanceSearchVerifier.verifyLargeBatchPreservesOrder();
        ChunkDistanceSearchVerifier.verifyNativeBatchPreservesOrder();
        ChunkDistanceSearchVerifier.verifyNegativeRadiusRejected();
        ChunkDistanceSearchVerifier.verifyAnyShortCircuitsBeforeLaterCandidates();
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

    private static void verifyNativeBridgeNearestIndexTieAndUnbounded() {
        double[] tiedPositions = {1.0, 0.0, 0.0, -1.0, 0.0, 0.0};
        int nearest = NativeBridge.findNearestIndex(0.0, 0.0, 0.0, -1.0, tiedPositions);
        assertEquals(0, nearest, "Native bridge nearest index tie order");

        int bounded = NativeBridge.findNearestIndex(0.0, 0.0, 0.0, 4.0, new double[] {9.0, 0.0, 0.0});
        int unbounded = NativeBridge.findNearestIndex(0.0, 0.0, 0.0, -1.0, new double[] {9.0, 0.0, 0.0});
        assertEquals(-1, bounded, "Native bridge nearest bounded rejection");
        assertEquals(0, unbounded, "Native bridge nearest unbounded acceptance");
    }

    private static void verifyNativeBridgeNearestIndexExclusive() {
        int boundary = NativeBridge.findNearestIndexExclusive(0.0, 0.0, 0.0, 4.0, new double[] {2.0, 0.0, 0.0, 1.0, 0.0, 0.0});
        assertEquals(1, boundary, "Native bridge exclusive nearest boundary");

        int unbounded = NativeBridge.findNearestIndexExclusive(0.0, 0.0, 0.0, -1.0, new double[] {2.0, 0.0, 0.0});
        assertEquals(0, unbounded, "Native bridge exclusive nearest unbounded");
    }

    private static void verifyNativeBridgeAnyWithinRadiusExclusive() {
        boolean boundary = NativeBridge.hasAnyWithinRadiusExclusive(0.0, 0.0, 0.0, 4.0, new double[] {2.0, 0.0, 0.0});
        boolean inner = NativeBridge.hasAnyWithinRadiusExclusive(0.0, 0.0, 0.0, 4.0, new double[] {2.0, 0.0, 0.0, 1.0, 0.0, 0.0});
        boolean unbounded = NativeBridge.hasAnyWithinRadiusExclusive(0.0, 0.0, 0.0, -1.0, new double[] {9.0, 0.0, 0.0});
        boolean emptyUnbounded = NativeBridge.hasAnyWithinRadiusExclusive(0.0, 0.0, 0.0, -1.0, new double[] {});

        assertEquals(false, boundary, "Native bridge exclusive any boundary");
        assertEquals(true, inner, "Native bridge exclusive any inner position");
        assertEquals(true, unbounded, "Native bridge exclusive any unbounded");
        assertEquals(false, emptyUnbounded, "Native bridge exclusive any empty unbounded");
    }

    private static void verifyNativeBridgeNearestBlockCenterIndex() {
        int nearest = NativeBridge.findNearestBlockCenterIndex(0.5, 0.5, 0.5, new int[] {1, 0, 0, -1, 0, 0, 0, 1, 0, 0, 1, 1});
        assertEquals(2, nearest, "Native bridge nearest block center tie order");

        int missing = NativeBridge.findNearestBlockCenterIndex(0.0, 0.0, 0.0, new int[] {});
        assertEquals(-1, missing, "Native bridge nearest block center empty input");
    }

    private static void verifyNativeBridgeNearestBlockCornerIndex() {
        int nearest = NativeBridge.findNearestBlockCornerIndex(0, 0, 0, new int[] {1, 0, 0, -1, 0, 0, 0, 2, 0});
        assertEquals(0, nearest, "Native bridge nearest block corner tie order");

        int missing = NativeBridge.findNearestBlockCornerIndex(0, 0, 0, new int[] {});
        assertEquals(-1, missing, "Native bridge nearest block corner empty input");
    }

    private static void verifyNativeBridgeFilterAndSort() {
        int[] positions = {0, 64, 0, 3, 68, 4, -1, 63, -2};
        int[] filtered = NativeBridge.filterWithinRadius(0, 64, 0, 40, positions);
        int[] output = new int[positions.length / 3];
        int count = NativeBridge.filterWithinRadius(0, 64, 0, 40, positions, output);
        int[] sorted = NativeBridge.sortByDistance(0, 64, 0, positions);
        assertArrayEquals(new int[] {0, 2}, filtered, "Native bridge radius filter");
        assertEquals(2, count, "Native bridge radius filter count");
        assertArrayEquals(new int[] {0, 2}, Arrays.copyOf(output, count), "Native bridge radius filter output prefix");
        assertArrayEquals(new int[] {0, 2, 1}, sorted, "Native bridge distance sort");
    }

    private static void verifyNativeBridgeDoubleSort() {
        double[] positions = {0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0};
        int[] sorted = NativeBridge.sortByDistance(0.0, 64.0, 0.0, positions);
        assertArrayEquals(new int[] {0, 2, 1}, sorted, "Native bridge double distance sort");
    }

    private static void verifyNativeBridgeDoubleFusedSortAndRadiusPrefix() {
        double[] positions = {
            2.0, 0.0, 0.0,
            1.0, 0.0, 0.0,
            -1.0, 0.0, 0.0,
            4.0, 0.0, 0.0
        };
        int[] output = {-1, -1, -1, -1, 77};
        int count = NativeBridge.sortByDistanceAndCountWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            4.0,
            positions,
            output
        );

        assertEquals(2, count, "Native bridge fused distance sort radius prefix count");
        assertArrayEquals(new int[] {1, 2, 0, 3}, Arrays.copyOf(output, 4), "Native bridge fused distance sort full order");
        assertEquals(77, output[4], "Native bridge fused distance sort extra output capacity");

        int[] boundaryOutput = new int[4];
        int boundaryCount = NativeBridge.sortByDistanceAndCountWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            1.0,
            positions,
            boundaryOutput
        );
        assertEquals(0, boundaryCount, "Native bridge fused distance sort exact boundary prefix");
        assertArrayEquals(new int[] {1, 2, 0, 3}, boundaryOutput, "Native bridge fused distance sort boundary order");

        int[] nanOutput = new int[4];
        int nanCount = NativeBridge.sortByDistanceAndCountWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            Double.NaN,
            positions,
            nanOutput
        );
        assertEquals(0, nanCount, "Native bridge fused distance sort NaN radius prefix");
        assertArrayEquals(new int[] {1, 2, 0, 3}, nanOutput, "Native bridge fused distance sort NaN radius order");

        int[] infinityOutput = new int[4];
        int infinityCount = NativeBridge.sortByDistanceAndCountWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            Double.POSITIVE_INFINITY,
            positions,
            infinityOutput
        );
        assertEquals(4, infinityCount, "Native bridge fused distance sort infinite radius prefix");
        assertArrayEquals(new int[] {1, 2, 0, 3}, infinityOutput, "Native bridge fused distance sort infinite radius order");
    }

    private static void verifyNativeBridgeNearestSelection() {
        double[] positions = {
            4.0, 0.0, 0.0,
            1.0, 0.0, 0.0,
            -1.0, 0.0, 0.0,
            2.0, 0.0, 0.0,
            3.0, 0.0, 0.0
        };
        int[] output = {77, 88, 99};
        int count = NativeBridge.selectNearestIndicesWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            16.0,
            positions,
            2,
            output
        );

        assertEquals(2, count, "Native bridge nearest selection count");
        assertArrayEquals(new int[] {1, 2, 99}, output, "Native bridge nearest selection output");

        int nanCount = NativeBridge.selectNearestIndicesWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            Double.NaN,
            positions,
            2,
            output
        );
        assertEquals(0, nanCount, "Native bridge nearest selection NaN radius count");
        assertArrayEquals(new int[] {1, 2, 99}, output, "Native bridge nearest selection NaN radius tail");
    }

    private static void verifyNativeBridgeBlockSort() {
        int[] positions = {0, 64, 0, 3, 68, 4, -1, 63, -2};
        int[] sorted = NativeBridge.sortByBlockDistance(0, 64, 0, positions);
        assertArrayEquals(new int[] {0, 2, 1}, sorted, "Native bridge block distance sort");
    }

    private static void verifyNativeBridgeDoubleFilter() {
        double[] positions = descendingAxisPositionsDouble(5000);
        int[] filtered = NativeBridge.filterWithinRadius(0.0, 0.0, 0.0, 1024.0, positions);
        int[] exclusiveFiltered = NativeBridge.filterWithinRadiusExclusive(0.0, 0.0, 0.0, 1024.0, positions);
        int[] output = new int[positions.length / 3];
        int count = NativeBridge.filterWithinRadius(0.0, 0.0, 0.0, 1024.0, positions, output);
        int[] exclusiveOutput = new int[positions.length / 3];
        int exclusiveCount = NativeBridge.filterWithinRadiusExclusive(0.0, 0.0, 0.0, 1024.0, positions, exclusiveOutput);
        int[] sortedExclusiveFiltered = NativeBridge.sortWithinRadiusExclusive(0.0, 0.0, 0.0, 1024.0, positions);
        int[] sortedExclusiveOutput = new int[positions.length / 3];
        int sortedExclusiveCount = NativeBridge.sortWithinRadiusExclusive(0.0, 0.0, 0.0, 1024.0, positions, sortedExclusiveOutput);
        assertArrayEquals(range(4967, 5000), filtered, "Native bridge large double radius filter");
        assertArrayEquals(range(4968, 5000), exclusiveFiltered, "Native bridge large exclusive double radius filter");
        assertEquals(33, count, "Native bridge large double radius count");
        assertArrayEquals(range(4967, 5000), Arrays.copyOf(output, count), "Native bridge large double radius output prefix");
        assertEquals(32, exclusiveCount, "Native bridge large exclusive double radius count");
        assertArrayEquals(range(4968, 5000), Arrays.copyOf(exclusiveOutput, exclusiveCount), "Native bridge large exclusive double radius output prefix");
        assertArrayEquals(descendingRange(4999, 4968), sortedExclusiveFiltered, "Native bridge sorted exclusive double radius filter");
        assertEquals(32, sortedExclusiveCount, "Native bridge sorted exclusive double radius count");
        assertArrayEquals(descendingRange(4999, 4968), Arrays.copyOf(sortedExclusiveOutput, sortedExclusiveCount), "Native bridge sorted exclusive double radius output prefix");
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
        int[] output = new int[positions.length / 3];
        int count = NativeBridge.filterWithinAabb(0.0, -1.0, -1.0, 33.0, 1.0, 1.0, positions, output);
        assertArrayEquals(range(4967, 5000), filtered, "Native bridge large AABB filter");
        assertEquals(33, count, "Native bridge large AABB filter count");
        assertArrayEquals(range(4967, 5000), Arrays.copyOf(output, count), "Native bridge large AABB filter output prefix");
    }

    private static void verifyNativeBridgeAabbIntersectionFilter() {
        double[] boxes = {
            0.0, 0.0, 0.0, 1.0, 1.0, 1.0,
            1.0, 0.0, 0.0, 2.0, 1.0, 1.0,
            -1.0, -1.0, -1.0, 0.0, 0.0, 0.0,
            0.5, 0.5, 0.5, 1.5, 1.5, 1.5
        };
        int[] edgeFiltered = NativeBridge.filterIntersectingAabb(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, boxes);
        int[] nativeEdgeOutput = new int[boxes.length / 6];
        int nativeEdgeCount = NativeBridge.filterIntersectingAabb(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, boxes, nativeEdgeOutput);
        assertArrayEquals(new int[] {0, 3}, edgeFiltered, "Native bridge AABB intersection edge semantics");
        assertEquals(2, nativeEdgeCount, "Native bridge AABB intersection edge count");
        assertArrayEquals(new int[] {0, 3}, Arrays.copyOf(nativeEdgeOutput, nativeEdgeCount), "Native bridge AABB intersection edge output prefix");

        double[] largeBoxes = descendingAxisBoxesDouble(5000);
        int[] filtered = NativeBridge.filterIntersectingAabb(0.25, -1.0, -1.0, 33.25, 2.0, 2.0, largeBoxes);
        int[] nativeOutput = new int[largeBoxes.length / 6];
        int nativeCount = NativeBridge.filterIntersectingAabb(0.25, -1.0, -1.0, 33.25, 2.0, 2.0, largeBoxes, nativeOutput);
        assertArrayEquals(range(4966, 5000), filtered, "Native bridge large AABB intersection filter");
        assertEquals(34, nativeCount, "Native bridge large AABB intersection count");
        assertArrayEquals(range(4966, 5000), Arrays.copyOf(nativeOutput, nativeCount), "Native bridge large AABB intersection output prefix");
    }

    private static void verifyNativeBridgePotentialEnergyChange() {
        int[] positions = {
            3, 0, 4,
            0, 0, 2,
            -6, 0, 8
        };
        double[] charges = {10.0, -4.0, 2.5};

        double expected = JavaComputeKernels.potentialEnergyChange(0, 0, 0, positions, charges, -3.0);
        double actual = NativeBridge.computePotentialEnergyChange(0, 0, 0, positions, charges, -3.0);
        assertEquals(expected, actual, "Native bridge potential energy change");
    }

    private static void verifyNativeBridgeLargeFilterAndSort() {
        int[] positions = descendingAxisPositions(5000);
        int[] filtered = NativeBridge.filterWithinRadius(0, 0, 0, 1024, positions);
        int[] output = new int[positions.length / 3];
        int count = NativeBridge.filterWithinRadius(0, 0, 0, 1024, positions, output);
        int[] sorted = NativeBridge.sortByDistance(0, 0, 0, positions);

        assertArrayEquals(range(4967, 5000), filtered, "Native bridge large radius filter");
        assertEquals(33, count, "Native bridge large radius filter count");
        assertArrayEquals(range(4967, 5000), Arrays.copyOf(output, count), "Native bridge large radius filter output prefix");
        assertArrayEquals(descendingRange(4999, 0), sorted, "Native bridge large distance sort");
    }

    private static void verifyNativeBridgeLargeBlockSort() {
        int[] positions = descendingAxisPositions(5000);
        int[] sorted = NativeBridge.sortByBlockDistance(0, 0, 0, positions);

        assertArrayEquals(descendingRange(4999, 0), sorted, "Native bridge large block distance sort");
    }

    private static void verifyNativeBridgeIntegerOverflowSemantics() {
        int[] positions = {Integer.MAX_VALUE, 0, 0};
        long[] actual = NativeBridge.computeSquaredDistances(Integer.MIN_VALUE, 0, 0, positions);
        assertArrayEquals(new long[] {-8_589_934_591L}, actual, "Native bridge integer overflow parity");
    }

    private static void verifyJavaFilterAndSort() {
        int[] positions = {0, 64, 0, 3, 68, 4, -1, 63, -2};
        int[] filtered = JavaComputeKernels.filterWithinRadius(0, 64, 0, 40, positions);
        int[] output = new int[positions.length / 3];
        int count = JavaComputeKernels.filterWithinRadius(0, 64, 0, 40, positions, output);
        int[] sorted = JavaComputeKernels.sortByDistance(0, 64, 0, positions);
        assertArrayEquals(new int[] {0, 2}, filtered, "Java radius filter");
        assertEquals(2, count, "Java radius filter count");
        assertArrayEquals(new int[] {0, 2}, Arrays.copyOf(output, count), "Java radius filter output prefix");
        assertArrayEquals(new int[] {0, 2, 1}, sorted, "Java distance sort");
    }

    private static void verifyJavaDoubleSort() {
        double[] positions = {0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0};
        int[] sorted = JavaComputeKernels.sortByDistance(0.0, 64.0, 0.0, positions);
        assertArrayEquals(new int[] {0, 2, 1}, sorted, "Java double distance sort");
    }

    private static void verifyJavaDoubleFusedSortAndRadiusPrefix() {
        double[] positions = {
            2.0, 0.0, 0.0,
            1.0, 0.0, 0.0,
            -1.0, 0.0, 0.0,
            4.0, 0.0, 0.0
        };
        int[] output = {-1, -1, -1, -1, 77};
        int count = JavaComputeKernels.sortByDistanceAndCountWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            4.0,
            positions,
            output
        );

        assertEquals(2, count, "Java fused distance sort radius prefix count");
        assertArrayEquals(new int[] {1, 2, 0, 3}, Arrays.copyOf(output, 4), "Java fused distance sort full order");
        assertEquals(77, output[4], "Java fused distance sort extra output capacity");

        int[] boundaryOutput = new int[4];
        int boundaryCount = JavaComputeKernels.sortByDistanceAndCountWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            1.0,
            positions,
            boundaryOutput
        );
        assertEquals(0, boundaryCount, "Java fused distance sort exact boundary prefix");

        int[] nanOutput = new int[4];
        int nanCount = JavaComputeKernels.sortByDistanceAndCountWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            Double.NaN,
            positions,
            nanOutput
        );
        assertEquals(0, nanCount, "Java fused distance sort NaN radius prefix");

        int[] infinityOutput = new int[4];
        int infinityCount = JavaComputeKernels.sortByDistanceAndCountWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            Double.POSITIVE_INFINITY,
            positions,
            infinityOutput
        );
        assertEquals(4, infinityCount, "Java fused distance sort infinite radius prefix");

        double[] tiedPositions = {
            1.0, 0.0, 0.0,
            -1.0, 0.0, 0.0,
            0.0, 2.0, 0.0
        };
        int[] tiedOutput = new int[3];
        int tiedCount = JavaComputeKernels.sortByDistanceAndCountWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            4.0,
            tiedPositions,
            tiedOutput
        );
        assertEquals(2, tiedCount, "Java fused distance sort tie prefix");
        assertArrayEquals(new int[] {0, 1, 2}, tiedOutput, "Java fused distance sort tie order");
    }

    private static void verifyJavaNearestSelection() {
        double[] positions = {
            4.0, 0.0, 0.0,
            1.0, 0.0, 0.0,
            -1.0, 0.0, 0.0,
            2.0, 0.0, 0.0,
            3.0, 0.0, 0.0
        };
        int[] output = {77, 88, 99};
        int count = JavaComputeKernels.selectNearestIndicesWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            16.0,
            positions,
            2,
            output
        );

        assertEquals(2, count, "Java nearest selection count");
        assertArrayEquals(new int[] {1, 2, 99}, output, "Java nearest selection output");

        int nanCount = JavaComputeKernels.selectNearestIndicesWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            Double.NaN,
            positions,
            2,
            output
        );
        assertEquals(0, nanCount, "Java nearest selection NaN radius count");
        assertArrayEquals(new int[] {1, 2, 99}, output, "Java nearest selection NaN radius tail");
    }

    private static void verifyJavaBlockSort() {
        int[] positions = {0, 64, 0, 3, 68, 4, -1, 63, -2};
        int[] sorted = JavaComputeKernels.sortByBlockDistance(0, 64, 0, positions);
        assertArrayEquals(new int[] {0, 2, 1}, sorted, "Java block distance sort");
    }

    private static void verifyJavaDoubleFilter() {
        double[] positions = descendingAxisPositionsDouble(5000);
        int[] filtered = JavaComputeKernels.filterWithinRadius(0.0, 0.0, 0.0, 1024.0, positions);
        int[] exclusiveFiltered = JavaComputeKernels.filterWithinRadiusExclusive(0.0, 0.0, 0.0, 1024.0, positions);
        int[] output = new int[positions.length / 3];
        int count = JavaComputeKernels.filterWithinRadius(0.0, 0.0, 0.0, 1024.0, positions, output);
        int[] exclusiveOutput = new int[positions.length / 3];
        int exclusiveCount = JavaComputeKernels.filterWithinRadiusExclusive(0.0, 0.0, 0.0, 1024.0, positions, exclusiveOutput);
        int[] sortedExclusiveFiltered = JavaComputeKernels.sortWithinRadiusExclusive(0.0, 0.0, 0.0, 1024.0, positions);
        int[] sortedExclusiveOutput = new int[positions.length / 3];
        int sortedExclusiveCount = JavaComputeKernels.sortWithinRadiusExclusive(0.0, 0.0, 0.0, 1024.0, positions, sortedExclusiveOutput);
        assertArrayEquals(range(4967, 5000), filtered, "Java large double radius filter");
        assertArrayEquals(range(4968, 5000), exclusiveFiltered, "Java large exclusive double radius filter");
        assertEquals(33, count, "Java large double radius count");
        assertArrayEquals(range(4967, 5000), Arrays.copyOf(output, count), "Java large double radius output prefix");
        assertEquals(32, exclusiveCount, "Java large exclusive double radius count");
        assertArrayEquals(range(4968, 5000), Arrays.copyOf(exclusiveOutput, exclusiveCount), "Java large exclusive double radius output prefix");
        assertArrayEquals(descendingRange(4999, 4968), sortedExclusiveFiltered, "Java sorted exclusive double radius filter");
        assertEquals(32, sortedExclusiveCount, "Java sorted exclusive double radius count");
        assertArrayEquals(descendingRange(4999, 4968), Arrays.copyOf(sortedExclusiveOutput, sortedExclusiveCount), "Java sorted exclusive double radius output prefix");
    }

    private static void verifyNativeBridgeVariableRadiusFilter() {
        double[] positions = {
            0.0, 8.0, 0.0,
            10.0, 0.0, 0.0,
            12.0, 0.0, 0.0,
            15.1, 0.0, 0.0
        };
        double[] radiiSquared = {64.0, 64.0, 144.0, 225.0};
        int[] filtered = NativeBridge.filterWithinRadii(0.0, 0.0, 0.0, positions, radiiSquared);
        int[] nativeOutput = new int[positions.length / 3];
        int nativeCount = NativeBridge.filterWithinRadii(0.0, 0.0, 0.0, positions, radiiSquared, nativeOutput);
        int[] javaOutput = new int[positions.length / 3];
        int javaCount = JavaComputeKernels.filterWithinRadii(0.0, 0.0, 0.0, positions, radiiSquared, javaOutput);
        assertArrayEquals(new int[] {0, 2}, filtered, "Native bridge variable radius filter");
        assertEquals(2, nativeCount, "Native bridge variable radius filter count");
        assertArrayEquals(new int[] {0, 2}, Arrays.copyOf(nativeOutput, nativeCount), "Native bridge variable radius filter output prefix");
        assertEquals(2, javaCount, "Java variable radius filter count");
        assertArrayEquals(new int[] {0, 2}, Arrays.copyOf(javaOutput, javaCount), "Java variable radius filter output prefix");
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
        int[] output = new int[positions.length / 3];
        int count = JavaComputeKernels.filterWithinAabb(0.0, -1.0, -1.0, 33.0, 1.0, 1.0, positions, output);
        assertArrayEquals(range(4967, 5000), filtered, "Java large AABB filter");
        assertEquals(33, count, "Java large AABB filter count");
        assertArrayEquals(range(4967, 5000), Arrays.copyOf(output, count), "Java large AABB filter output prefix");
    }

    private static void verifyJavaAabbIntersectionFilter() {
        double[] boxes = {
            0.0, 0.0, 0.0, 1.0, 1.0, 1.0,
            1.0, 0.0, 0.0, 2.0, 1.0, 1.0,
            -1.0, -1.0, -1.0, 0.0, 0.0, 0.0,
            0.5, 0.5, 0.5, 1.5, 1.5, 1.5
        };
        int[] edgeFiltered = JavaComputeKernels.filterIntersectingAabb(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, boxes);
        int[] javaEdgeOutput = new int[boxes.length / 6];
        int javaEdgeCount = JavaComputeKernels.filterIntersectingAabb(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, boxes, javaEdgeOutput);
        assertArrayEquals(new int[] {0, 3}, edgeFiltered, "Java AABB intersection edge semantics");
        assertEquals(2, javaEdgeCount, "Java AABB intersection edge count");
        assertArrayEquals(new int[] {0, 3}, Arrays.copyOf(javaEdgeOutput, javaEdgeCount), "Java AABB intersection edge output prefix");

        double[] largeBoxes = descendingAxisBoxesDouble(5000);
        int[] filtered = JavaComputeKernels.filterIntersectingAabb(0.25, -1.0, -1.0, 33.25, 2.0, 2.0, largeBoxes);
        int[] javaOutput = new int[largeBoxes.length / 6];
        int javaCount = JavaComputeKernels.filterIntersectingAabb(0.25, -1.0, -1.0, 33.25, 2.0, 2.0, largeBoxes, javaOutput);
        assertArrayEquals(range(4966, 5000), filtered, "Java large AABB intersection filter");
        assertEquals(34, javaCount, "Java large AABB intersection count");
        assertArrayEquals(range(4966, 5000), Arrays.copyOf(javaOutput, javaCount), "Java large AABB intersection output prefix");
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

    private static void verifyJavaNearestIndexTieAndUnbounded() {
        double[] tiedPositions = {1.0, 0.0, 0.0, -1.0, 0.0, 0.0};
        int nearest = JavaComputeKernels.findNearestIndex(0.0, 0.0, 0.0, -1.0, tiedPositions);
        assertEquals(0, nearest, "Java nearest index tie order");

        int bounded = JavaComputeKernels.findNearestIndex(0.0, 0.0, 0.0, 4.0, new double[] {9.0, 0.0, 0.0});
        int unbounded = JavaComputeKernels.findNearestIndex(0.0, 0.0, 0.0, -1.0, new double[] {9.0, 0.0, 0.0});
        assertEquals(-1, bounded, "Java nearest bounded rejection");
        assertEquals(0, unbounded, "Java nearest unbounded acceptance");
    }

    private static void verifyJavaNearestIndexExclusive() {
        int boundary = JavaComputeKernels.findNearestIndexExclusive(0.0, 0.0, 0.0, 4.0, new double[] {2.0, 0.0, 0.0, 1.0, 0.0, 0.0});
        assertEquals(1, boundary, "Java exclusive nearest boundary");

        int unbounded = JavaComputeKernels.findNearestIndexExclusive(0.0, 0.0, 0.0, -1.0, new double[] {2.0, 0.0, 0.0});
        assertEquals(0, unbounded, "Java exclusive nearest unbounded");
    }

    private static void verifyJavaAnyWithinRadiusExclusive() {
        boolean boundary = JavaComputeKernels.hasAnyWithinRadiusExclusive(0.0, 0.0, 0.0, 4.0, new double[] {2.0, 0.0, 0.0});
        boolean inner = JavaComputeKernels.hasAnyWithinRadiusExclusive(0.0, 0.0, 0.0, 4.0, new double[] {2.0, 0.0, 0.0, 1.0, 0.0, 0.0});
        boolean unbounded = JavaComputeKernels.hasAnyWithinRadiusExclusive(0.0, 0.0, 0.0, -1.0, new double[] {9.0, 0.0, 0.0});
        boolean emptyUnbounded = JavaComputeKernels.hasAnyWithinRadiusExclusive(0.0, 0.0, 0.0, -1.0, new double[] {});

        assertEquals(false, boundary, "Java exclusive any boundary");
        assertEquals(true, inner, "Java exclusive any inner position");
        assertEquals(true, unbounded, "Java exclusive any unbounded");
        assertEquals(false, emptyUnbounded, "Java exclusive any empty unbounded");
    }

    private static void verifyJavaNearestBlockCenterIndex() {
        int nearest = JavaComputeKernels.findNearestBlockCenterIndex(0.5, 0.5, 0.5, new int[] {1, 0, 0, -1, 0, 0, 0, 1, 0, 0, 1, 1});
        assertEquals(2, nearest, "Java nearest block center tie order");

        int missing = JavaComputeKernels.findNearestBlockCenterIndex(0.0, 0.0, 0.0, new int[] {});
        assertEquals(-1, missing, "Java nearest block center empty input");
    }

    private static void verifyJavaNearestBlockCenterIndexRejectsNullPositions() {
        try {
            JavaComputeKernels.findNearestBlockCenterIndex(0.0, 0.0, 0.0, (int[]) null);
        } catch (IllegalArgumentException expected) {
            return;
        }

        throw new AssertionError("Expected null packed positions to be rejected");
    }

    private static void verifyJavaNearestBlockCornerIndex() {
        int nearest = JavaComputeKernels.findNearestBlockCornerIndex(0, 0, 0, new int[] {1, 0, 0, -1, 0, 0, 0, 2, 0});
        assertEquals(0, nearest, "Java nearest block corner tie order");

        int missing = JavaComputeKernels.findNearestBlockCornerIndex(0, 0, 0, new int[] {});
        assertEquals(-1, missing, "Java nearest block corner empty input");
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

    private static void assertEquals(double expected, double actual, String label) {
        if (Double.compare(expected, actual) != 0) {
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

    private static double[] descendingAxisBoxesDouble(int count) {
        double[] boxes = new double[count * 6];
        for (int index = 0; index < count; index++) {
            double min = count - 1 - index;
            int offset = index * 6;
            boxes[offset] = min;
            boxes[offset + 1] = 0.0;
            boxes[offset + 2] = 0.0;
            boxes[offset + 3] = min + 0.5;
            boxes[offset + 4] = 1.0;
            boxes[offset + 5] = 1.0;
        }
        return boxes;
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
