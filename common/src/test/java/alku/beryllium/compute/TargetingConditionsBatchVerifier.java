package alku.beryllium.compute;

import java.util.ArrayList;
import java.util.List;

public final class TargetingConditionsBatchVerifier {
    private TargetingConditionsBatchVerifier() {
    }

    public static void verifyFilterCandidatesWithinAabb() {
        verifyFilterCandidatesWithinAabb(sizedPoints(12), 3, 8);
        verifyFilterCandidatesWithinAabb(sizedPoints(40), 10, 30);
        verifyAabbEdgeSemantics();
    }

    public static void verifyFilterByConstantDistanceBeforePosttest() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 3.0, 0.0, 0.0),
            new SimplePoint(1, 1.0, 0.0, 0.0),
            new SimplePoint(2, 0.0, 2.0, 0.0),
            new SimplePoint(3, 0.0, 0.0, 1.0)
        );
        List<Integer> posttested = new ArrayList<>();

        List<SimplePoint> actual = TargetingConditionsBatch.filterByConstantDistanceAndPosttest(
            points,
            EntityPacking.packPositions(points, point -> point.x, point -> point.y, point -> point.z),
            0.0,
            0.0,
            0.0,
            4.0,
            point -> {
                posttested.add(point.id);
                return point.id != 2;
            }
        );

        List<SimplePoint> expected = List.of(points.get(1), points.get(3));
        if (!expected.equals(actual)) {
            throw new AssertionError("distance-filtered posttest mismatch, expected " + expected + " but got " + actual);
        }

        List<Integer> expectedPosttested = List.of(1, 2, 3);
        if (!expectedPosttested.equals(posttested)) {
            throw new AssertionError("posttest order mismatch, expected " + expectedPosttested + " but got " + posttested);
        }

        verifyLargeFilterByConstantDistanceBeforePosttest();
    }

    public static void verifyFilterByVariableDistanceBeforePosttest() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 3.0, 0.0, 0.0),
            new SimplePoint(1, 1.0, 0.0, 0.0),
            new SimplePoint(2, 0.0, 2.0, 0.0),
            new SimplePoint(3, 0.0, 0.0, 4.0)
        );
        double[] radiiSquared = {4.0, 1.0, 4.0, 16.0};
        List<Integer> posttested = new ArrayList<>();

        List<SimplePoint> actual = TargetingConditionsBatch.filterByVariableDistanceAndPosttest(
            points,
            EntityPacking.packPositions(points, point -> point.x, point -> point.y, point -> point.z),
            0.0,
            0.0,
            0.0,
            radiiSquared,
            point -> {
                posttested.add(point.id);
                return point.id != 2;
            }
        );

        List<SimplePoint> expected = List.of(points.get(1), points.get(3));
        if (!expected.equals(actual)) {
            throw new AssertionError("variable-distance posttest mismatch, expected " + expected + " but got " + actual);
        }

        List<Integer> expectedPosttested = List.of(1, 2, 3);
        if (!expectedPosttested.equals(posttested)) {
            throw new AssertionError("variable-distance posttest order mismatch, expected " + expectedPosttested + " but got " + posttested);
        }
    }

    public static void verifyFindNearestAfterVariableDistanceAndPosttest() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 9.0, 0.0, 0.0),
            new SimplePoint(1, 1.0, 0.0, 0.0),
            new SimplePoint(2, 0.0, 2.0, 0.0),
            new SimplePoint(3, 0.0, 0.0, 4.0),
            new SimplePoint(4, 0.0, 0.0, 0.5)
        );
        double[] radiiSquared = {4.0, 1.0, 4.0, 16.0, 0.20};
        List<Integer> posttested = new ArrayList<>();

        SimplePoint actual = NearestEntitySearch.findNearestAfterVariableDistanceAndPosttest(
            points,
            EntityPacking.packPositions(points, point -> point.x, point -> point.y, point -> point.z),
            0.0,
            0.0,
            0.0,
            radiiSquared,
            point -> {
                posttested.add(point.id);
                return point.id != 2;
            }
        );

        if (!points.get(1).equals(actual)) {
            throw new AssertionError("variable-distance nearest mismatch, expected " + points.get(1) + " but got " + actual);
        }

        List<Integer> expectedPosttested = List.of(1, 2, 3);
        if (!expectedPosttested.equals(posttested)) {
            throw new AssertionError("variable-distance nearest posttest order mismatch, expected " + expectedPosttested + " but got " + posttested);
        }
    }

    public static void verifyFindNearestAfterConstantDistanceUsesSeparateDistanceOrigin() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 4.0, 0.0, 0.0),
            new SimplePoint(1, 6.0, 0.0, 0.0),
            new SimplePoint(2, 3.0, 0.0, 0.0)
        );
        List<Integer> posttested = new ArrayList<>();

        SimplePoint actual = NearestEntitySearch.findNearestAfterConstantDistanceAndPosttest(
            points,
            EntityPacking.packPositions(points, point -> point.x, point -> point.y, point -> point.z),
            0.0,
            0.0,
            0.0,
            100.0,
            0.0,
            0.0,
            25.0,
            point -> {
                posttested.add(point.id);
                return true;
            }
        );

        if (!points.get(0).equals(actual)) {
            throw new AssertionError("separate-origin constant nearest mismatch, expected " + points.get(0) + " but got " + actual);
        }

        List<Integer> expectedPosttested = List.of(0, 2);
        if (!expectedPosttested.equals(posttested)) {
            throw new AssertionError("separate-origin constant posttest order mismatch, expected " + expectedPosttested + " but got " + posttested);
        }
    }

    public static void verifyFindNearestAfterVariableDistanceUsesSeparateDistanceOrigin() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 4.0, 0.0, 0.0),
            new SimplePoint(1, 6.0, 0.0, 0.0),
            new SimplePoint(2, 3.0, 0.0, 0.0)
        );
        double[] radiiSquared = {25.0, 25.0, 25.0};
        List<Integer> posttested = new ArrayList<>();

        SimplePoint actual = NearestEntitySearch.findNearestAfterVariableDistanceAndPosttest(
            points,
            EntityPacking.packPositions(points, point -> point.x, point -> point.y, point -> point.z),
            0.0,
            0.0,
            0.0,
            100.0,
            0.0,
            0.0,
            radiiSquared,
            point -> {
                posttested.add(point.id);
                return true;
            }
        );

        if (!points.get(0).equals(actual)) {
            throw new AssertionError("separate-origin variable nearest mismatch, expected " + points.get(0) + " but got " + actual);
        }

        List<Integer> expectedPosttested = List.of(0, 2);
        if (!expectedPosttested.equals(posttested)) {
            throw new AssertionError("separate-origin variable posttest order mismatch, expected " + expectedPosttested + " but got " + posttested);
        }
    }

    public static void verifyFindNearestAfterPrecomputedDistanceUsesSeparateDistanceOrigin() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 4.0, 0.0, 0.0),
            new SimplePoint(1, 6.0, 0.0, 0.0),
            new SimplePoint(2, 3.0, 0.0, 0.0)
        );
        double[] radiiSquared = {25.0, Double.NaN, 25.0};
        List<Integer> posttested = new ArrayList<>();

        SimplePoint actual = NearestEntitySearch.findNearestAfterPrecomputedDistanceAndPosttest(
            points,
            EntityPacking.packPositions(points, point -> point.x, point -> point.y, point -> point.z),
            0.0,
            0.0,
            0.0,
            100.0,
            0.0,
            0.0,
            radiiSquared,
            point -> {
                posttested.add(point.id);
                return true;
            }
        );

        if (!points.get(1).equals(actual)) {
            throw new AssertionError("separate-origin precomputed nearest mismatch, expected " + points.get(1) + " but got " + actual);
        }

        List<Integer> expectedPosttested = List.of(0, 1, 2);
        if (!expectedPosttested.equals(posttested)) {
            throw new AssertionError("separate-origin precomputed posttest order mismatch, expected " + expectedPosttested + " but got " + posttested);
        }
    }

    public static void verifyFindNearestAfterPrecomputedDistanceAcceptsNaNRadius() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 3.0, 0.0, 0.0),
            new SimplePoint(1, 1.0, 0.0, 0.0)
        );
        double[] radiiSquared = {Double.NaN, 0.25};

        SimplePoint actual = NearestEntitySearch.findNearestAfterPrecomputedDistanceAndPosttest(
            points,
            EntityPacking.packPositions(points, point -> point.x, point -> point.y, point -> point.z),
            0.0,
            0.0,
            0.0,
            radiiSquared,
            point -> true
        );

        if (!points.get(0).equals(actual)) {
            throw new AssertionError("NaN radius nearest mismatch, expected " + points.get(0) + " but got " + actual);
        }
    }

    private static void verifyFilterCandidatesWithinAabb(List<SimplePoint> points, int minInclusive, int maxExclusive) {
        double min = minInclusive;
        double max = maxExclusive;

        List<SimplePoint> actual = TargetingConditionsBatch.filterCandidatesWithinAabb(
            points,
            point -> point.x,
            point -> point.y,
            point -> point.z,
            new net.minecraft.world.phys.AABB(min, min, min, max, max, max)
        );

        List<SimplePoint> expected = points.subList(minInclusive, maxExclusive);
        if (!expected.equals(actual)) {
            throw new AssertionError("AABB helper mismatch, expected " + expected + " but got " + actual);
        }
    }

    private static void verifyAabbEdgeSemantics() {
        List<SimplePoint> points = sizedPoints(40);

        List<SimplePoint> edgeActual = TargetingConditionsBatch.filterCandidatesWithinAabb(
            points,
            point -> point.x,
            point -> point.y,
            point -> point.z,
            new net.minecraft.world.phys.AABB(39.0, 39.0, 39.0, 40.0, 40.0, 40.0)
        );

        List<SimplePoint> edgeExpected = List.of(points.get(39));
        if (!edgeExpected.equals(edgeActual)) {
            throw new AssertionError("AABB edge semantics mismatch, expected " + edgeExpected + " but got " + edgeActual);
        }
    }

    private static void verifyLargeFilterByConstantDistanceBeforePosttest() {
        List<SimplePoint> points = axisPoints(40);
        List<Integer> posttested = new ArrayList<>();

        List<SimplePoint> actual = TargetingConditionsBatch.filterByConstantDistanceAndPosttest(
            points,
            EntityPacking.packPositions(points, point -> point.x, point -> point.y, point -> point.z),
            0.0,
            0.0,
            0.0,
            25.0,
            point -> {
                posttested.add(point.id);
                return point.id != 3;
            }
        );

        List<SimplePoint> expected = List.of(points.get(0), points.get(1), points.get(2), points.get(4), points.get(5));
        if (!expected.equals(actual)) {
            throw new AssertionError("large distance-filtered posttest mismatch, expected " + expected + " but got " + actual);
        }

        List<Integer> expectedPosttested = List.of(0, 1, 2, 3, 4, 5);
        if (!expectedPosttested.equals(posttested)) {
            throw new AssertionError("large posttest order mismatch, expected " + expectedPosttested + " but got " + posttested);
        }
    }

    private static List<SimplePoint> sizedPoints(int size) {
        List<SimplePoint> points = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            points.add(new SimplePoint(index, index, index + 0.25, index + 0.5));
        }

        return points;
    }

    private static List<SimplePoint> axisPoints(int size) {
        List<SimplePoint> points = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            points.add(new SimplePoint(index, index, 0.0, 0.0));
        }

        return points;
    }

    private record SimplePoint(int id, double x, double y, double z) {
    }
}
