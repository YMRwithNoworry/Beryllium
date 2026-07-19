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

    public static void verifyFilterWithinExclusiveDistanceSortedByDistance() {
        List<SimplePoint> points = descendingAxisPoints(40);

        List<SimplePoint> matches = EntityDistanceSort.filterWithinExclusiveDistanceSortedByDistance(
            points,
            0.0,
            0.0,
            0.0,
            4.0,
            point -> point.x,
            point -> point.y,
            point -> point.z
        );

        assertListEquals(List.of(39, 38, 37, 36), ids(matches), "exclusive sorted entity distance filter order");
    }

    public static void verifyFilterWithinExclusiveDistanceSortedByDistanceTieOrder() {
        List<SimplePoint> points = new ArrayList<>();
        points.add(new SimplePoint(0, 1.0, 0.0, 0.0));
        points.add(new SimplePoint(1, -1.0, 0.0, 0.0));
        points.add(new SimplePoint(2, 2.0, 0.0, 0.0));

        List<SimplePoint> matches = EntityDistanceSort.filterWithinExclusiveDistanceSortedByDistance(
            points,
            0.0,
            0.0,
            0.0,
            2.0,
            point -> point.x,
            point -> point.y,
            point -> point.z
        );

        assertListEquals(List.of(0, 1), ids(matches), "exclusive sorted entity distance filter tie order");
    }

    public static void verifyFilterWithinExclusiveDistanceSortedByDistancePostFilterOrder() {
        List<SimplePoint> points = descendingAxisPoints(40);
        List<Integer> postFiltered = new ArrayList<>();

        List<SimplePoint> matches = EntityDistanceSort.filterWithinExclusiveDistanceSortedByDistance(
            points,
            0.0,
            0.0,
            0.0,
            4.0,
            point -> point.x,
            point -> point.y,
            point -> point.z,
            point -> {
                postFiltered.add(point.id);
                return point.id % 2 == 0;
            }
        );

        assertListEquals(List.of(38, 36), ids(matches), "exclusive sorted entity distance filter post-filter result");
        assertListEquals(List.of(36, 37, 38, 39), postFiltered, "exclusive sorted entity distance filter post-filter order");
    }

    public static void verifyFindFirstWithinExclusiveDistanceSortedByDistanceShortCircuitsAfterSort() {
        List<SimplePoint> points = descendingAxisPoints(40);
        List<Integer> postFiltered = new ArrayList<>();

        SimplePoint match = EntityDistanceSort.findFirstWithinExclusiveDistanceSortedByDistance(
                points,
                0.0,
                0.0,
                0.0,
                4.0,
                point -> point.x,
                point -> point.y,
                point -> point.z,
                point -> {
                    postFiltered.add(point.id);
                    return point.id == 38;
                }
            )
            .orElseThrow(() -> new AssertionError("expected a sorted-radius match"));

        if (match.id != 38) {
            throw new AssertionError("exclusive sorted entity distance find-first mismatch, expected 38 but got " + match.id);
        }
        assertListEquals(List.of(39, 38), postFiltered, "exclusive sorted entity distance find-first post-filter order");
    }

    public static void verifyFindFirstWithinExclusiveDistanceAfterPredicatesSortedByDistancePreservesFilterOrder() {
        List<SimplePoint> points = new ArrayList<>();
        points.add(new SimplePoint(0, 12.0, 0.0, 0.0));
        points.add(new SimplePoint(1, 3.0, 0.0, 0.0));
        points.add(new SimplePoint(2, 1.0, 0.0, 0.0));
        for (int index = 3; index < 40; index++) {
            points.add(new SimplePoint(index, 20.0 + index, 0.0, 0.0));
        }
        List<Integer> beforeDistance = new ArrayList<>();
        List<Integer> afterDistance = new ArrayList<>();

        SimplePoint match = EntityDistanceSort.findFirstWithinExclusiveDistanceAfterPredicatesSortedByDistance(
                points,
                0.0,
                0.0,
                0.0,
                4.0,
                point -> {
                    beforeDistance.add(point.id);
                    return point.id != 0;
                },
                point -> {
                    afterDistance.add(point.id);
                    return point.id != 2;
                },
                point -> point.x,
                point -> point.y,
                point -> point.z
            )
            .orElseThrow(() -> new AssertionError("expected a predicate-gated sorted-radius match"));

        if (match.id != 1) {
            throw new AssertionError("predicate-gated sorted find-first mismatch, expected 1 but got " + match.id);
        }
        assertListEquals(range(0, 40), beforeDistance, "predicate-gated sorted before-distance order");
        assertListEquals(List.of(1, 2), afterDistance, "predicate-gated sorted after-distance order");
    }

    public static void verifyFindFirstWithinExclusiveDistanceAfterPredicatesSortedByDistanceTieOrder() {
        List<SimplePoint> points = new ArrayList<>();
        points.add(new SimplePoint(0, 1.0, 0.0, 0.0));
        points.add(new SimplePoint(1, -1.0, 0.0, 0.0));
        points.add(new SimplePoint(2, 2.0, 0.0, 0.0));

        SimplePoint match = EntityDistanceSort.findFirstWithinExclusiveDistanceAfterPredicatesSortedByDistance(
                points,
                0.0,
                0.0,
                0.0,
                4.0,
                point -> true,
                point -> point.id != 0,
                point -> point.x,
                point -> point.y,
                point -> point.z
            )
            .orElseThrow(() -> new AssertionError("expected a tied predicate-gated sorted match"));

        if (match.id != 1) {
            throw new AssertionError("predicate-gated sorted tie find-first mismatch, expected 1 but got " + match.id);
        }
    }

    public static void verifyFindFirstWithinExclusiveDistanceAfterPredicatesSelectsNearestWithoutShortCircuiting() {
        List<SimplePoint> points = descendingAxisPoints(1024);
        List<Integer> beforeDistance = new ArrayList<>();
        List<Integer> afterDistance = new ArrayList<>();

        SimplePoint match = EntityDistanceSort.findFirstWithinExclusiveDistanceAfterPredicatesSortedByDistance(
                points,
                0.0,
                0.0,
                0.0,
                2048.0,
                point -> {
                    beforeDistance.add(point.id);
                    return true;
                },
                point -> {
                    afterDistance.add(point.id);
                    return point.id != 1023;
                },
                point -> point.x,
                point -> point.y,
                point -> point.z
            )
            .orElseThrow(() -> new AssertionError("expected a nearest predicate-gated match"));

        if (match.id != 1022) {
            throw new AssertionError("linear nearest selection mismatch, expected 1022 but got " + match.id);
        }
        assertListEquals(range(0, 1024), beforeDistance, "linear nearest before-distance predicate order");
        assertListEquals(range(0, 1024), afterDistance, "linear nearest after-distance predicate order");
    }

    public static void verifyFindFirstSortedByDistanceShortCircuitsAfterSort() {
        List<SimplePoint> points = descendingAxisPoints(40);
        List<Integer> postFiltered = new ArrayList<>();

        SimplePoint match = EntityDistanceSort.findFirstSortedByDistance(
                points,
                0.0,
                0.0,
                0.0,
                point -> point.x,
                point -> point.y,
                point -> point.z,
                point -> {
                    postFiltered.add(point.id);
                    return point.id == 38;
                }
            )
            .orElseThrow(() -> new AssertionError("expected a sorted match"));

        if (match.id != 38) {
            throw new AssertionError("sorted entity distance find-first mismatch, expected 38 but got " + match.id);
        }
        assertListEquals(List.of(39, 38), postFiltered, "sorted entity distance find-first post-filter order");
    }

    public static void verifyFindFirstSortedByDistancePreservesTieOrder() {
        List<SimplePoint> points = new ArrayList<>();
        points.add(new SimplePoint(0, 1.0, 0.0, 0.0));
        points.add(new SimplePoint(1, -1.0, 0.0, 0.0));
        points.add(new SimplePoint(2, 2.0, 0.0, 0.0));
        List<Integer> postFiltered = new ArrayList<>();

        SimplePoint match = EntityDistanceSort.findFirstSortedByDistance(
                points,
                0.0,
                0.0,
                0.0,
                point -> point.x,
                point -> point.y,
                point -> point.z,
                point -> {
                    postFiltered.add(point.id);
                    return point.id == 1;
                }
            )
            .orElseThrow(() -> new AssertionError("expected a tied sorted match"));

        if (match.id != 1) {
            throw new AssertionError("sorted entity distance tie find-first mismatch, expected 1 but got " + match.id);
        }
        assertListEquals(List.of(0, 1), postFiltered, "sorted entity distance tie post-filter order");
    }

    public static void verifyFindFirstSortedWithinExclusiveDistanceEvaluatesPredicateBeforeRadius() {
        List<SimplePoint> points = new ArrayList<>();
        points.add(new SimplePoint(0, 1.0, 0.0, 0.0));
        points.add(new SimplePoint(1, 2.0, 0.0, 0.0));
        points.add(new SimplePoint(2, 12.0, 0.0, 0.0));
        points.add(new SimplePoint(3, 13.0, 0.0, 0.0));
        for (int index = 4; index < 40; index++) {
            points.add(new SimplePoint(index, 20.0 + index, 0.0, 0.0));
        }
        List<Integer> beforeDistance = new ArrayList<>();
        List<Integer> afterDistance = new ArrayList<>();

        var match = EntityDistanceSort.findFirstSortedByDistanceWithinExclusiveDistanceAfterPredicate(
            points,
            0.0,
            0.0,
            0.0,
            10.0,
            point -> {
                beforeDistance.add(point.id);
                return point.id != 0;
            },
            point -> {
                afterDistance.add(point.id);
                return false;
            },
            point -> point.x,
            point -> point.y,
            point -> point.z
        );

        if (match.isPresent()) {
            throw new AssertionError("expected no sorted-radius match but got " + match.get().id);
        }
        assertListEquals(range(0, 40), beforeDistance, "sorted entity distance before-radius predicate order");
        assertListEquals(List.of(1), afterDistance, "sorted entity distance after-radius predicate order");
    }

    public static void verifyFindFirstSortedWithinExclusiveDistanceShortCircuitsAfterRadius() {
        List<SimplePoint> points = descendingAxisPoints(40);
        List<Integer> beforeDistance = new ArrayList<>();
        List<Integer> afterDistance = new ArrayList<>();

        SimplePoint match = EntityDistanceSort.findFirstSortedByDistanceWithinExclusiveDistanceAfterPredicate(
                points,
                0.0,
                0.0,
                0.0,
                4.0,
                point -> {
                    beforeDistance.add(point.id);
                    return true;
                },
                point -> {
                    afterDistance.add(point.id);
                    return point.id == 38;
                },
                point -> point.x,
                point -> point.y,
                point -> point.z
            )
            .orElseThrow(() -> new AssertionError("expected a sorted-radius match"));

        if (match.id != 38) {
            throw new AssertionError("sorted-radius entity distance find-first mismatch, expected 38 but got " + match.id);
        }
        assertListEquals(List.of(39, 38), beforeDistance, "sorted-radius entity distance before-radius short-circuit order");
        assertListEquals(List.of(39, 38), afterDistance, "sorted-radius entity distance after-radius short-circuit order");
    }

    public static void verifyNearestItemTopKHitPreservesPredicateOrder() {
        List<SimplePoint> points = descendingAxisPoints(1024);
        List<Integer> beforeDistance = new ArrayList<>();
        List<Integer> afterDistance = new ArrayList<>();

        SimplePoint match = EntityDistanceSort.findFirstSortedByDistanceWithinExclusiveDistanceAfterPredicate(
                points,
                0.0,
                0.0,
                0.0,
                2048.0,
                point -> {
                    beforeDistance.add(point.id);
                    return true;
                },
                point -> {
                    afterDistance.add(point.id);
                    return point.id == 1021;
                },
                point -> point.x,
                point -> point.y,
                point -> point.z
            )
            .orElseThrow(() -> new AssertionError("expected a Top-K match"));

        if (match.id != 1021) {
            throw new AssertionError("Top-K match mismatch, expected 1021 but got " + match.id);
        }
        assertListEquals(descendingRange(1023, 1021), beforeDistance, "Top-K hit before-distance predicate order");
        assertListEquals(descendingRange(1023, 1021), afterDistance, "Top-K hit after-distance predicate order");
    }

    public static void verifyNearestItemTopKFallbackPreservesPredicateOrder() {
        List<SimplePoint> points = descendingAxisPoints(1024);
        List<Integer> beforeDistance = new ArrayList<>();
        List<Integer> afterDistance = new ArrayList<>();

        SimplePoint match = EntityDistanceSort.findFirstSortedByDistanceWithinExclusiveDistanceAfterPredicate(
                points,
                0.0,
                0.0,
                0.0,
                2048.0,
                point -> {
                    beforeDistance.add(point.id);
                    return true;
                },
                point -> {
                    afterDistance.add(point.id);
                    return point.id == 998;
                },
                point -> point.x,
                point -> point.y,
                point -> point.z
            )
            .orElseThrow(() -> new AssertionError("expected a Top-K fallback match"));

        if (match.id != 998) {
            throw new AssertionError("Top-K fallback match mismatch, expected 998 but got " + match.id);
        }
        assertListEquals(descendingRange(1023, 998), beforeDistance, "Top-K fallback before-distance predicate order");
        assertListEquals(descendingRange(1023, 998), afterDistance, "Top-K fallback after-distance predicate order");
    }

    public static void verifyFindFirstBySortedOrderWithinPrefixPreservesPredicateOrderAndShortCircuits() {
        List<SimplePoint> points = List.of(
            new SimplePoint(0, 1.0, 0.0, 0.0),
            new SimplePoint(1, 2.0, 0.0, 0.0),
            new SimplePoint(2, 3.0, 0.0, 0.0),
            new SimplePoint(3, 4.0, 0.0, 0.0)
        );
        int[] order = {0, 1, 2, 3};
        List<Integer> beforeDistance = new ArrayList<>();
        List<Integer> afterDistance = new ArrayList<>();

        var noMatch = EntityDistanceSort.findFirstBySortedOrderWithinPrefix(
            points,
            order,
            2,
            point -> {
                beforeDistance.add(point.id);
                return true;
            },
            point -> {
                afterDistance.add(point.id);
                return false;
            }
        );

        if (noMatch.isPresent()) {
            throw new AssertionError("expected no prefix match but got " + noMatch.get().id);
        }
        assertListEquals(List.of(0, 1, 2, 3), beforeDistance, "prefix helper before predicate order");
        assertListEquals(List.of(0, 1), afterDistance, "prefix helper after predicate prefix order");

        beforeDistance.clear();
        afterDistance.clear();
        var match = EntityDistanceSort.findFirstBySortedOrderWithinPrefix(
            points,
            new int[] {1, 0, 2, 3},
            2,
            point -> {
                beforeDistance.add(point.id);
                return point.id != 1;
            },
            point -> {
                afterDistance.add(point.id);
                return true;
            }
        ).orElseThrow(() -> new AssertionError("expected a prefix match"));

        if (match.id != 0) {
            throw new AssertionError("prefix helper match mismatch, expected 0 but got " + match.id);
        }
        assertListEquals(List.of(1, 0), beforeDistance, "prefix helper short-circuit before order");
        assertListEquals(List.of(0), afterDistance, "prefix helper short-circuit after order");
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

    private static List<Integer> range(int startInclusive, int endExclusive) {
        List<Integer> result = new ArrayList<>(endExclusive - startInclusive);
        for (int value = startInclusive; value < endExclusive; value++) {
            result.add(value);
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
