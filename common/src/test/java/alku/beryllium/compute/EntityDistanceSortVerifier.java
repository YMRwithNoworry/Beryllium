package alku.beryllium.compute;

import java.util.ArrayList;
import java.util.List;

public final class EntityDistanceSortVerifier {
    private EntityDistanceSortVerifier() {
    }

    public static void verifySortByDistance() {
        List<SimplePoint> points = descendingAxisPoints(40);

        EntityDistanceSort.sortByDistance(points, 0.0, 0.0, 0.0, point -> point.x, point -> point.y, point -> point.z);

        assertListEquals(descendingRange(39, 0), ids(points), "entity distance sort order");
    }

    public static void verifySortByDistanceTieOrder() {
        List<SimplePoint> points = new ArrayList<>();
        points.add(new SimplePoint(0, 1.0, 0.0, 0.0));
        points.add(new SimplePoint(1, -1.0, 0.0, 0.0));
        points.add(new SimplePoint(2, 2.0, 0.0, 0.0));

        EntityDistanceSort.sortByDistance(points, 0.0, 0.0, 0.0, point -> point.x, point -> point.y, point -> point.z);

        assertListEquals(List.of(0, 1, 2), ids(points), "entity distance sort tie order");
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

    private static List<Integer> descendingRange(int startInclusive, int endInclusive) {
        List<Integer> result = new ArrayList<>(startInclusive - endInclusive + 1);
        for (int value = startInclusive; value >= endInclusive; value--) {
            result.add(value);
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
