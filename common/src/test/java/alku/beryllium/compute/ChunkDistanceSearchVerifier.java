package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Regression checks for ChunkMap's spectator-aware chunk distance query.
 */
public final class ChunkDistanceSearchVerifier {
    private ChunkDistanceSearchVerifier() {
    }

    public static void verifyExclusiveBoundaryAndEncounterOrder() {
        List<Candidate> candidates = List.of(
            new Candidate(100.0, 0.0, true),
            new Candidate(128.0, 0.0, true),
            new Candidate(127.5, 0.0, false),
            new Candidate(0.0, 127.5, true),
            new Candidate(-127.5, 0.0, true)
        );

        List<Candidate> matches = ChunkDistanceSearch.filterWithinExclusiveDistance(
            candidates,
            0.0,
            0.0,
            128.0 * 128.0,
            Candidate::eligible,
            Candidate::x,
            Candidate::z
        );

        assertEquals(
            List.of(candidates.get(0), candidates.get(3), candidates.get(4)),
            matches,
            "chunk distance filter boundary and encounter order"
        );
    }

    public static void verifyEligibilityRunsBeforeCoordinateAccess() {
        List<Candidate> candidates = List.of(
            new Candidate(1.0, 1.0, false),
            new Candidate(2.0, 2.0, true)
        );
        List<Boolean> predicateOrder = new ArrayList<>();

        List<Candidate> matches = ChunkDistanceSearch.filterWithinExclusiveDistance(
            candidates,
            0.0,
            0.0,
            10.0 * 10.0,
            candidate -> {
                predicateOrder.add(candidate.eligible());
                return candidate.eligible();
            },
            candidate -> {
                if (!candidate.eligible()) {
                    throw new AssertionError("coordinate getter accessed before eligibility predicate");
                }
                return candidate.x();
            },
            Candidate::z
        );

        assertEquals(List.of(false, true), predicateOrder, "chunk distance predicate order");
        assertEquals(List.of(candidates.get(1)), matches, "chunk distance eligibility filtering");
    }

    public static void verifyLargeBatchPreservesOrder() {
        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < 128; index++) {
            candidates.add(new Candidate(
                index % 2 == 0 ? 1.0 + index : 200.0 + index,
                0.0,
                index % 5 != 0
            ));
        }

        List<Candidate> matches = ChunkDistanceSearch.filterWithinExclusiveDistance(
            candidates,
            0.0,
            0.0,
            128.0 * 128.0,
            Candidate::eligible,
            Candidate::x,
            Candidate::z
        );

        List<Candidate> expected = candidates.stream()
            .filter(Candidate::eligible)
            .filter(candidate -> candidate.x() * candidate.x() < 128.0 * 128.0)
            .toList();
        assertEquals(expected, matches, "large chunk distance order");
    }

    public static void verifyNativeBatchPreservesOrder() {
        double[] positions = new double[128 * 2];
        for (int index = 0; index < 128; index++) {
            positions[index * 2] = index % 2 == 0 ? 1.0 + index : 200.0 + index;
            positions[index * 2 + 1] = 0.0;
        }

        int[] output = new int[128];
        int count = NativeBridge.filterWithinExclusiveChunkDistance(
            0.0,
            0.0,
            128.0 * 128.0,
            positions,
            output
        );

        int expectedCount = 0;
        for (int index = 0; index < 128; index++) {
            if (positions[index * 2] * positions[index * 2] < 128.0 * 128.0) {
                if (output[expectedCount] != index) {
                    throw new AssertionError("native chunk distance order mismatch at " + expectedCount);
                }
                expectedCount++;
            }
        }

        if (count != expectedCount) {
            throw new AssertionError("native chunk distance count mismatch, expected " + expectedCount + " but got " + count);
        }
    }

    public static void verifyNegativeRadiusRejected() {
        try {
            ChunkDistanceSearch.filterWithinExclusiveDistance(
                List.of(new Candidate(0.0, 0.0, true)),
                0.0,
                0.0,
                -1.0,
                Candidate::eligible,
                Candidate::x,
                Candidate::z
            );
        } catch (IllegalArgumentException expected) {
            return;
        }

        throw new AssertionError("negative chunk distance radius should be rejected");
    }

    public static void verifyAnyShortCircuitsBeforeLaterCandidates() {
        List<Candidate> candidates = List.of(
            new Candidate(1.0, 0.0, true),
            new Candidate(2.0, 0.0, true)
        );
        List<Integer> predicateOrder = new ArrayList<>();
        List<Integer> coordinateOrder = new ArrayList<>();

        boolean match = ChunkDistanceSearch.anyWithinExclusiveDistance(
            candidates,
            0.0,
            0.0,
            128.0 * 128.0,
            candidate -> {
                predicateOrder.add((int) candidate.x());
                return candidate.eligible();
            },
            candidate -> {
                coordinateOrder.add((int) candidate.x());
                return candidate.x();
            },
            Candidate::z
        );

        assertEquals(true, match, "chunk distance any result");
        assertEquals(List.of(1), predicateOrder, "chunk distance any predicate short circuit");
        assertEquals(List.of(1), coordinateOrder, "chunk distance any coordinate short circuit");
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private record Candidate(double x, double z, boolean eligible) {
    }
}
