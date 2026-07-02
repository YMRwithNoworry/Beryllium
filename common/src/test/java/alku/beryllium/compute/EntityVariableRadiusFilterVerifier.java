package alku.beryllium.compute;

import java.util.ArrayList;
import java.util.List;

public final class EntityVariableRadiusFilterVerifier {
    private EntityVariableRadiusFilterVerifier() {
    }

    public static void verifyFilterWithinInclusiveDistances() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 8.0, 0.0, 0.0, 64.0),
            new SimplePoint(1, 10.0, 0.0, 0.0, 64.0),
            new SimplePoint(2, 12.0, 0.0, 0.0, 144.0),
            new SimplePoint(3, 15.1, 0.0, 0.0, 225.0)
        );

        List<SimplePoint> matches = EntityVariableRadiusFilter.filterWithinInclusiveDistances(
            points,
            0.0,
            0.0,
            0.0,
            point -> point.radiusSquared,
            point -> point.x,
            point -> point.y,
            point -> point.z
        );

        assertListEquals(List.of(0, 2), ids(matches), "variable inclusive distance filter");
    }

    public static void verifyFilterWithinInclusiveDistancesPreservesOrder() {
        List<SimplePoint> points = descendingAxisPoints(40);

        List<SimplePoint> matches = EntityVariableRadiusFilter.filterWithinInclusiveDistances(
            points,
            0.0,
            0.0,
            0.0,
            point -> point.radiusSquared,
            point -> point.x,
            point -> point.y,
            point -> point.z
        );

        assertListEquals(List.of(0, 10, 20, 30, 39), ids(matches), "variable inclusive distance filter order");
    }

    private static List<SimplePoint> descendingAxisPoints(int count) {
        List<SimplePoint> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double x = count - 1 - index;
            double radiusSquared = index % 10 == 0 || index == count - 1 ? x * x : 0.25;
            points.add(new SimplePoint(index, x, 0.0, 0.0, radiusSquared));
        }
        return points;
    }

    private static List<Integer> ids(List<SimplePoint> points) {
        List<Integer> result = new ArrayList<>(points.size());
        for (SimplePoint point : points) {
            result.add(point.id);
        }
        return result;
    }

    private static void assertListEquals(List<Integer> expected, List<Integer> actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private record SimplePoint(int id, double x, double y, double z, double radiusSquared) {
    }
}
