package alku.beryllium.compute;

import java.util.ArrayList;
import java.util.List;

public final class EntityDistanceFilterVerifier {
    private EntityDistanceFilterVerifier() {
    }

    public static void verifyFilterWithinExclusiveDistance() {
        List<SimplePoint> points = descendingAxisPoints(40);

        List<SimplePoint> matches = EntityDistanceFilter.filterWithinExclusiveDistance(
            points,
            0.0,
            0.0,
            0.0,
            4.0,
            point -> point.x,
            point -> point.y,
            point -> point.z
        );

        assertListEquals(List.of(36, 37, 38, 39), ids(matches), "exclusive entity distance filter order");
    }

    public static void verifyFilterWithinExclusiveDistanceRejectsBoundary() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 2.0, 0.0, 0.0),
            new SimplePoint(1, 1.0, 0.0, 0.0)
        );

        List<SimplePoint> matches = EntityDistanceFilter.filterWithinExclusiveDistance(
            points,
            0.0,
            0.0,
            0.0,
            2.0,
            point -> point.x,
            point -> point.y,
            point -> point.z
        );

        assertListEquals(List.of(1), ids(matches), "exclusive entity distance filter boundary");
    }

    private static List<SimplePoint> descendingAxisPoints(int count) {
        List<SimplePoint> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            points.add(new SimplePoint(index, count - 1 - index, 0.0, 0.0));
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

    private record SimplePoint(int id, double x, double y, double z) {
    }
}
