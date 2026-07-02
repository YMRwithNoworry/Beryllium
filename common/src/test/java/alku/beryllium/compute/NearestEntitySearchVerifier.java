package alku.beryllium.compute;

import java.util.ArrayList;
import java.util.List;

public final class NearestEntitySearchVerifier {
    private NearestEntitySearchVerifier() {
    }

    public static void verifyHasAnyWithinExclusiveDistanceShortCircuits() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 2.0, 0.0, 0.0, true),
            new SimplePoint(1, 9.0, 0.0, 0.0, false),
            new SimplePoint(2, 1.0, 0.0, 0.0, true),
            new SimplePoint(3, 0.0, 0.0, 0.0, true)
        );
        List<Integer> pretested = new ArrayList<>();
        List<Integer> distanceChecked = new ArrayList<>();

        boolean match = NearestEntitySearch.hasAnyWithinExclusiveDistance(
            points,
            point -> {
                pretested.add(point.id);
                return point.pretest;
            },
            4.0,
            0.0,
            0.0,
            0.0,
            point -> {
                distanceChecked.add(point.id);
                return point.x;
            },
            point -> point.y,
            point -> point.z
        );

        assertEquals(true, match, "exclusive any match");
        assertListEquals(List.of(0, 1, 2), pretested, "exclusive any predicate order");
        assertListEquals(List.of(0, 2), distanceChecked, "exclusive any distance order");
    }

    public static void verifyHasAnyWithinExclusiveDistanceSkipsDistanceForUnboundedMatch() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 2.0, 0.0, 0.0, false),
            new SimplePoint(1, 1.0, 0.0, 0.0, true),
            new SimplePoint(2, 0.0, 0.0, 0.0, true)
        );
        List<Integer> pretested = new ArrayList<>();

        boolean match = NearestEntitySearch.hasAnyWithinExclusiveDistance(
            points,
            point -> {
                pretested.add(point.id);
                return point.pretest;
            },
            -1.0,
            0.0,
            0.0,
            0.0,
            point -> {
                throw new AssertionError("Unbounded any check should not read coordinates");
            },
            point -> {
                throw new AssertionError("Unbounded any check should not read coordinates");
            },
            point -> {
                throw new AssertionError("Unbounded any check should not read coordinates");
            }
        );

        assertEquals(true, match, "unbounded any match");
        assertListEquals(List.of(0, 1), pretested, "unbounded any predicate order");
    }

    public static void verifyHasAnyWithinExclusiveDistanceChecksAllWhenMissing() {
        List<SimplePoint> points = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            points.add(new SimplePoint(index, 2.0 + index, 0.0, 0.0, index % 2 == 0));
        }
        List<Integer> pretested = new ArrayList<>();
        List<Integer> distanceChecked = new ArrayList<>();

        boolean match = NearestEntitySearch.hasAnyWithinExclusiveDistance(
            points,
            point -> {
                pretested.add(point.id);
                return point.pretest;
            },
            4.0,
            0.0,
            0.0,
            0.0,
            point -> {
                distanceChecked.add(point.id);
                return point.x;
            },
            point -> point.y,
            point -> point.z
        );

        assertEquals(false, match, "exclusive any missing");
        assertListEquals(range(0, 40), pretested, "exclusive any missing predicate order");
        assertListEquals(evenRange(0, 40), distanceChecked, "exclusive any missing distance order");
    }

    public static void verifyHasAnyWithinExclusiveDistanceBatchesPredicateBeforeDistance() {
        List<SimplePoint> points = new ArrayList<>();
        points.add(new SimplePoint(0, 1.0, 0.0, 0.0, false));
        points.add(new SimplePoint(1, 3.0, 0.0, 0.0, true));
        points.add(new SimplePoint(2, 1.0, 0.0, 0.0, true));
        for (int index = 3; index < 40; index++) {
            points.add(new SimplePoint(index, 20.0 + index, 0.0, 0.0, index % 2 == 0));
        }
        List<Integer> pretested = new ArrayList<>();
        List<Integer> distancePacked = new ArrayList<>();

        boolean match = NearestEntitySearch.hasAnyWithinExclusiveDistance(
            points,
            point -> {
                pretested.add(point.id);
                return point.pretest;
            },
            4.0,
            0.0,
            0.0,
            0.0,
            point -> {
                distancePacked.add(point.id);
                return point.x;
            },
            point -> point.y,
            point -> point.z
        );

        assertEquals(true, match, "large exclusive any match");
        assertListEquals(range(0, 40), pretested, "large exclusive any predicate order");
        assertListEquals(pretestedCandidatesForBatch(), distancePacked, "large exclusive any distance packing order");
    }

    private static List<Integer> pretestedCandidatesForBatch() {
        List<Integer> result = new ArrayList<>();
        result.add(1);
        result.addAll(evenRange(2, 40));
        return result;
    }

    private static List<Integer> range(int startInclusive, int endExclusive) {
        List<Integer> result = new ArrayList<>(endExclusive - startInclusive);
        for (int value = startInclusive; value < endExclusive; value++) {
            result.add(value);
        }
        return result;
    }

    private static List<Integer> evenRange(int startInclusive, int endExclusive) {
        List<Integer> result = new ArrayList<>();
        for (int value = startInclusive; value < endExclusive; value += 2) {
            result.add(value);
        }
        return result;
    }

    private static void assertEquals(boolean expected, boolean actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private static void assertListEquals(List<Integer> expected, List<Integer> actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private record SimplePoint(int id, double x, double y, double z, boolean pretest) {
    }
}
