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

    private static List<SimplePoint> sizedPoints(int size) {
        List<SimplePoint> points = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            points.add(new SimplePoint(index, index + 0.25, index + 0.5));
        }

        return points;
    }

    private record SimplePoint(double x, double y, double z) {
    }
}
