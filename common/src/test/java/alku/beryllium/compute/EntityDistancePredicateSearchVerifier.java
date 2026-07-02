package alku.beryllium.compute;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class EntityDistancePredicateSearchVerifier {
    private EntityDistancePredicateSearchVerifier() {
    }

    public static void verifyFindFirstWithinExclusiveDistancePreservesPredicateOrder() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 12.0, 0.0, 0.0, true, true),
            new SimplePoint(1, 2.0, 0.0, 0.0, false, true),
            new SimplePoint(2, 10.0, 0.0, 0.0, true, true),
            new SimplePoint(3, 9.0, 0.0, 0.0, true, false),
            new SimplePoint(4, 1.0, 0.0, 0.0, true, true)
        );
        List<Integer> pretested = new ArrayList<>();
        List<Integer> posttested = new ArrayList<>();

        Optional<SimplePoint> match = EntityDistancePredicateSearch.findFirstWithinExclusiveDistance(
            points,
            0.0,
            0.0,
            0.0,
            100.0,
            point -> {
                pretested.add(point.id);
                return point.pretest;
            },
            point -> {
                posttested.add(point.id);
                return point.posttest;
            },
            point -> point.x,
            point -> point.y,
            point -> point.z
        );

        assertEquals(4, match.orElseThrow().id, "exclusive first match");
        assertListEquals(List.of(0, 1, 2, 3, 4), pretested, "exclusive pretest order");
        assertListEquals(List.of(3, 4), posttested, "exclusive posttest order");
    }

    public static void verifyFindFirstWithinExclusiveDistanceBatchesLargeLists() {
        List<SimplePoint> points = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            points.add(new SimplePoint(index, index, 0.0, 0.0, true, index == 39));
        }
        List<Integer> posttested = new ArrayList<>();

        Optional<SimplePoint> match = EntityDistancePredicateSearch.findFirstWithinExclusiveDistance(
            points,
            0.0,
            0.0,
            0.0,
            1600.0,
            point -> true,
            point -> {
                posttested.add(point.id);
                return point.posttest;
            },
            point -> point.x,
            point -> point.y,
            point -> point.z
        );

        assertEquals(39, match.orElseThrow().id, "large exclusive first match");
        assertListEquals(range(0, 40), posttested, "large exclusive posttest order");
    }

    public static void verifyFindFirstWithinExclusiveDistanceBatchesDistanceGateBeforePosttest() {
        List<SimplePoint> points = new ArrayList<>();
        points.add(new SimplePoint(0, 12.0, 0.0, 0.0, true, true));
        points.add(new SimplePoint(1, 1.0, 0.0, 0.0, false, true));
        points.add(new SimplePoint(2, 10.0, 0.0, 0.0, true, true));
        points.add(new SimplePoint(3, 9.0, 0.0, 0.0, true, false));
        points.add(new SimplePoint(4, 1.0, 0.0, 0.0, true, true));
        for (int index = 5; index < 40; index++) {
            points.add(new SimplePoint(index, index, 0.0, 0.0, true, true));
        }

        List<Integer> pretested = new ArrayList<>();
        List<Integer> posttested = new ArrayList<>();

        Optional<SimplePoint> match = EntityDistancePredicateSearch.findFirstWithinExclusiveDistance(
            points,
            0.0,
            0.0,
            0.0,
            100.0,
            point -> {
                pretested.add(point.id);
                return point.pretest;
            },
            point -> {
                posttested.add(point.id);
                return point.posttest;
            },
            point -> point.x,
            point -> point.y,
            point -> point.z
        );

        assertEquals(4, match.orElseThrow().id, "large exclusive gated first match");
        assertListEquals(List.of(0, 1, 2, 3, 4), pretested, "large exclusive pretest before distance gate order");
        assertListEquals(List.of(3, 4), posttested, "large exclusive posttest after distance gate order");
    }

    public static void verifyFindFirstWithinInclusiveDistanceAcceptsBoundaryAndShortCircuits() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 2.0, 0.0, 0.0, true, true),
            new SimplePoint(1, 1.0, 0.0, 0.0, true, true)
        );
        List<Integer> pretested = new ArrayList<>();
        List<Integer> posttested = new ArrayList<>();
        List<Integer> distanceChecked = new ArrayList<>();

        Optional<SimplePoint> match = EntityDistancePredicateSearch.findFirstWithinInclusiveDistance(
            points,
            0.0,
            0.0,
            0.0,
            4.0,
            point -> {
                pretested.add(point.id);
                return point.pretest;
            },
            point -> {
                posttested.add(point.id);
                return point.posttest;
            },
            point -> {
                distanceChecked.add(point.id);
                return point.x;
            },
            point -> point.y,
            point -> point.z
        );

        assertEquals(0, match.orElseThrow().id, "inclusive boundary first match");
        assertListEquals(List.of(0), pretested, "inclusive boundary pretest short-circuit");
        assertListEquals(List.of(0), distanceChecked, "inclusive boundary distance short-circuit");
        assertListEquals(List.of(0), posttested, "inclusive boundary posttest short-circuit");
    }

    public static void verifyFindFirstWithinInclusiveDistanceBatchesDistanceGateBeforePosttest() {
        List<SimplePoint> points = new ArrayList<>();
        points.add(new SimplePoint(0, 12.0, 0.0, 0.0, true, true));
        points.add(new SimplePoint(1, 2.0, 0.0, 0.0, true, true));
        for (int index = 2; index < 40; index++) {
            points.add(new SimplePoint(index, index, 0.0, 0.0, true, true));
        }

        List<Integer> pretested = new ArrayList<>();
        List<Integer> posttested = new ArrayList<>();

        Optional<SimplePoint> match = EntityDistancePredicateSearch.findFirstWithinInclusiveDistance(
            points,
            0.0,
            0.0,
            0.0,
            4.0,
            point -> {
                pretested.add(point.id);
                return point.pretest;
            },
            point -> {
                posttested.add(point.id);
                return point.posttest;
            },
            point -> point.x,
            point -> point.y,
            point -> point.z
        );

        assertEquals(1, match.orElseThrow().id, "large inclusive boundary first match");
        assertListEquals(List.of(0, 1), pretested, "large inclusive pretest before distance gate order");
        assertListEquals(List.of(1), posttested, "large inclusive posttest after distance gate order");
    }

    private static List<Integer> range(int startInclusive, int endExclusive) {
        List<Integer> result = new ArrayList<>(endExclusive - startInclusive);
        for (int value = startInclusive; value < endExclusive; value++) {
            result.add(value);
        }
        return result;
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private static void assertListEquals(List<Integer> expected, List<Integer> actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private record SimplePoint(int id, double x, double y, double z, boolean pretest, boolean posttest) {
    }
}
