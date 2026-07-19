package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Sorts entity-like lists by squared distance while preserving vanilla stable tie order.
 */
public final class EntityDistanceSort {
    private static final int NEAREST_ITEM_TOP_K_LIMIT = 16;

    private EntityDistanceSort() {
    }

    public static <T extends Entity> void sortByDistance(List<T> entities, Entity origin) {
        sortByDistance(
            entities,
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            Entity::getX,
            Entity::getY,
            Entity::getZ
        );
    }

    public static <T> void sortByDistance(
        List<T> values,
        double originX,
        double originY,
        double originZ,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter
    ) {
        if (values.size() < 2) {
            return;
        }

        if (!NativeBatching.shouldUseNativeEntityBatch(values.size())) {
            values.sort(Comparator.comparingDouble(value -> squaredDistance(originX, originY, originZ, xGetter, yGetter, zGetter, value)));
            return;
        }

        double[] positions = EntityPacking.packPositions(values, xGetter, yGetter, zGetter);
        reorder(values, NativeBridge.sortByDistance(originX, originY, originZ, positions));
    }

    public static <T> List<T> filterWithinExclusiveDistanceSortedByDistance(
        List<? extends T> values,
        double originX,
        double originY,
        double originZ,
        double radius,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter
    ) {
        if (radius < 0.0) {
            throw new IllegalArgumentException("radius must be non-negative");
        }
        if (values.isEmpty()) {
            return List.of();
        }

        double radiusSquared = radius * radius;
        if (!NativeBatching.shouldUseNativeEntityBatch(values.size())) {
            List<T> matches = new ArrayList<>();
            for (T value : values) {
                if (squaredDistance(originX, originY, originZ, xGetter, yGetter, zGetter, value) < radiusSquared) {
                    matches.add(value);
                }
            }
            sortByDistance(matches, originX, originY, originZ, xGetter, yGetter, zGetter);
            return matches;
        }

        double[] positions = EntityPacking.packPositions(values, xGetter, yGetter, zGetter);
        int[] order = new int[values.size()];
        int orderCount = NativeBridge.sortWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions, order);
        List<T> matches = new ArrayList<>(orderCount);
        for (int orderIndex = 0; orderIndex < orderCount; orderIndex++) {
            matches.add(values.get(order[orderIndex]));
        }
        return matches;
    }

    public static <T> Optional<T> findFirstWithinExclusiveDistanceSortedByDistance(
        List<? extends T> values,
        double originX,
        double originY,
        double originZ,
        double radius,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter,
        Predicate<? super T> postSortedPredicate
    ) {
        if (radius < 0.0) {
            throw new IllegalArgumentException("radius must be non-negative");
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }

        double radiusSquared = radius * radius;
        if (!NativeBatching.shouldUseNativeEntityBatch(values.size())) {
            List<T> matches = new ArrayList<>();
            for (T value : values) {
                if (squaredDistance(originX, originY, originZ, xGetter, yGetter, zGetter, value) < radiusSquared) {
                    matches.add(value);
                }
            }
            sortByDistance(matches, originX, originY, originZ, xGetter, yGetter, zGetter);
            for (T match : matches) {
                if (postSortedPredicate.test(match)) {
                    return Optional.of(match);
                }
            }
            return Optional.empty();
        }

        double[] positions = EntityPacking.packPositions(values, xGetter, yGetter, zGetter);
        int[] order = new int[values.size()];
        int orderCount = NativeBridge.sortWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions, order);
        for (int orderIndex = 0; orderIndex < orderCount; orderIndex++) {
            int index = order[orderIndex];
            T value = values.get(index);
            if (postSortedPredicate.test(value)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    public static <T> Optional<T> findFirstWithinExclusiveDistanceAfterPredicatesSortedByDistance(
        List<? extends T> values,
        double originX,
        double originY,
        double originZ,
        double radius,
        Predicate<? super T> beforeDistancePredicate,
        Predicate<? super T> afterDistancePredicate,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter
    ) {
        if (radius < 0.0) {
            throw new IllegalArgumentException("radius must be non-negative");
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }

        List<T> beforeDistanceMatches = new ArrayList<>(values.size());
        for (T value : values) {
            if (beforeDistancePredicate.test(value)) {
                beforeDistanceMatches.add(value);
            }
        }
        if (beforeDistanceMatches.isEmpty()) {
            return Optional.empty();
        }

        double radiusSquared = radius * radius;
        if (NativeBatching.shouldUseNativeEntityBatch(beforeDistanceMatches.size())) {
            double[] positions = EntityPacking.packPositions(beforeDistanceMatches, xGetter, yGetter, zGetter);
            return findNearestWithinExclusivePackedDistance(
                beforeDistanceMatches,
                positions,
                originX,
                originY,
                originZ,
                radiusSquared,
                afterDistancePredicate
            );
        }

        List<T> matches = new ArrayList<>();
        for (T value : beforeDistanceMatches) {
            if (squaredDistance(originX, originY, originZ, xGetter, yGetter, zGetter, value) < radiusSquared
                && afterDistancePredicate.test(value)) {
                matches.add(value);
            }
        }
        return findNearestByCurrentDistance(matches, originX, originY, originZ, xGetter, yGetter, zGetter);
    }

    public static <T> Optional<T> findFirstSortedByDistance(
        List<? extends T> values,
        double originX,
        double originY,
        double originZ,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter,
        Predicate<? super T> postSortedPredicate
    ) {
        if (values.isEmpty()) {
            return Optional.empty();
        }

        if (!NativeBatching.shouldUseNativeEntityBatch(values.size())) {
            List<T> sortedValues = new ArrayList<>(values);
            sortByDistance(sortedValues, originX, originY, originZ, xGetter, yGetter, zGetter);
            for (T value : sortedValues) {
                if (postSortedPredicate.test(value)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }

        double[] positions = EntityPacking.packPositions(values, xGetter, yGetter, zGetter);
        int[] order = NativeBridge.sortByDistance(originX, originY, originZ, positions);
        for (int index : order) {
            T value = values.get(index);
            if (postSortedPredicate.test(value)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    public static <T> Optional<T> findFirstSortedByDistanceWithinExclusiveDistanceAfterPredicate(
        List<? extends T> values,
        double originX,
        double originY,
        double originZ,
        double radius,
        Predicate<? super T> beforeDistancePredicate,
        Predicate<? super T> afterDistancePredicate,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter
    ) {
        if (radius < 0.0) {
            throw new IllegalArgumentException("radius must be non-negative");
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }

        double radiusSquared = radius * radius;
        if (!NativeBatching.shouldUseNativeEntityBatch(values.size())) {
            List<T> sortedValues = new ArrayList<>(values);
            sortByDistance(sortedValues, originX, originY, originZ, xGetter, yGetter, zGetter);
            for (T value : sortedValues) {
                if (beforeDistancePredicate.test(value)
                    && squaredDistance(originX, originY, originZ, xGetter, yGetter, zGetter, value) < radiusSquared
                    && afterDistancePredicate.test(value)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }

        double[] positions = EntityPacking.packPositions(values, xGetter, yGetter, zGetter);
        if (NativeBatching.shouldUseNativeNearestItemTopK(values.size())) {
            int[] nearestOrder = new int[Math.min(NEAREST_ITEM_TOP_K_LIMIT, values.size())];
            int nearestCount = NativeBridge.selectNearestIndicesWithinRadiusExclusive(
                originX,
                originY,
                originZ,
                radiusSquared,
                positions,
                NEAREST_ITEM_TOP_K_LIMIT,
                nearestOrder
            );
            for (int orderIndex = 0; orderIndex < nearestCount; orderIndex++) {
                T value = values.get(nearestOrder[orderIndex]);
                if (beforeDistancePredicate.test(value) && afterDistancePredicate.test(value)) {
                    return Optional.of(value);
                }
            }

            boolean[] alreadyEvaluated = new boolean[values.size()];
            for (int orderIndex = 0; orderIndex < nearestCount; orderIndex++) {
                alreadyEvaluated[nearestOrder[orderIndex]] = true;
            }

            int[] order = new int[values.size()];
            int orderCount = NativeBridge.sortByDistanceAndCountWithinRadiusExclusive(
                originX,
                originY,
                originZ,
                radiusSquared,
                positions,
                order
            );
            return findFirstBySortedOrderWithinPrefixSkippingEvaluated(
                values,
                order,
                orderCount,
                beforeDistancePredicate,
                afterDistancePredicate,
                alreadyEvaluated
            );
        }

        int[] order = new int[values.size()];
        int orderCount = NativeBridge.sortByDistanceAndCountWithinRadiusExclusive(
            originX,
            originY,
            originZ,
            radiusSquared,
            positions,
            order
        );
        return findFirstBySortedOrderWithinPrefix(
            values,
            order,
            orderCount,
            beforeDistancePredicate,
            afterDistancePredicate
        );
    }

    static <T> Optional<T> findFirstBySortedOrderWithinPrefix(
        List<? extends T> values,
        int[] order,
        int prefixCount,
        Predicate<? super T> beforeDistancePredicate,
        Predicate<? super T> afterDistancePredicate
    ) {
        if (prefixCount < 0 || prefixCount > order.length) {
            throw new IllegalArgumentException("prefixCount must be within the order bounds");
        }

        for (int orderIndex = 0; orderIndex < order.length; orderIndex++) {
            T value = values.get(order[orderIndex]);
            if (beforeDistancePredicate.test(value)
                && orderIndex < prefixCount
                && afterDistancePredicate.test(value)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static <T> Optional<T> findFirstBySortedOrderWithinPrefixSkippingEvaluated(
        List<? extends T> values,
        int[] order,
        int prefixCount,
        Predicate<? super T> beforeDistancePredicate,
        Predicate<? super T> afterDistancePredicate,
        boolean[] alreadyEvaluated
    ) {
        if (alreadyEvaluated.length != values.size()) {
            throw new IllegalArgumentException("alreadyEvaluated must contain one entry per value");
        }

        for (int orderIndex = 0; orderIndex < order.length; orderIndex++) {
            int valueIndex = order[orderIndex];
            if (alreadyEvaluated[valueIndex]) {
                continue;
            }

            T value = values.get(valueIndex);
            if (beforeDistancePredicate.test(value)
                && orderIndex < prefixCount
                && afterDistancePredicate.test(value)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static <T> Optional<T> findNearestByCurrentDistance(
        List<? extends T> values,
        double originX,
        double originY,
        double originZ,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter
    ) {
        T nearest = null;
        double nearestDistance = 0.0;
        for (T value : values) {
            double distance = squaredDistance(originX, originY, originZ, xGetter, yGetter, zGetter, value);
            if (nearest == null || Double.compare(distance, nearestDistance) < 0) {
                nearest = value;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static <T> Optional<T> findNearestWithinExclusivePackedDistance(
        List<? extends T> values,
        double[] positions,
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        Predicate<? super T> afterDistancePredicate
    ) {
        T nearest = null;
        double nearestDistance = 0.0;
        for (int index = 0; index < values.size(); index++) {
            double distance = squaredDistanceAt(originX, originY, originZ, positions, index);
            if (!(distance < radiusSquared)) {
                continue;
            }

            T value = values.get(index);
            if (!afterDistancePredicate.test(value)) {
                continue;
            }
            if (nearest == null || Double.compare(distance, nearestDistance) < 0) {
                nearest = value;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public static <T> List<T> filterWithinExclusiveDistanceSortedByDistance(
        List<? extends T> values,
        double originX,
        double originY,
        double originZ,
        double radius,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter,
        Predicate<? super T> postDistancePredicate
    ) {
        if (radius < 0.0) {
            throw new IllegalArgumentException("radius must be non-negative");
        }
        if (values.isEmpty()) {
            return List.of();
        }

        double radiusSquared = radius * radius;
        if (!NativeBatching.shouldUseNativeEntityBatch(values.size())) {
            List<T> matches = new ArrayList<>();
            for (T value : values) {
                if (squaredDistance(originX, originY, originZ, xGetter, yGetter, zGetter, value) < radiusSquared
                    && postDistancePredicate.test(value)) {
                    matches.add(value);
                }
            }
            sortByDistance(matches, originX, originY, originZ, xGetter, yGetter, zGetter);
            return matches;
        }

        double[] positions = EntityPacking.packPositions(values, xGetter, yGetter, zGetter);
        int[] order = new int[values.size()];
        int orderCount = NativeBridge.sortWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions, order);
        boolean[] withinRadius = new boolean[values.size()];
        for (int orderIndex = 0; orderIndex < orderCount; orderIndex++) {
            int index = order[orderIndex];
            withinRadius[index] = true;
        }

        boolean[] accepted = new boolean[values.size()];
        for (int index = 0; index < values.size(); index++) {
            if (withinRadius[index] && postDistancePredicate.test(values.get(index))) {
                accepted[index] = true;
            }
        }

        List<T> matches = new ArrayList<>(orderCount);
        for (int orderIndex = 0; orderIndex < orderCount; orderIndex++) {
            int index = order[orderIndex];
            if (accepted[index]) {
                matches.add(values.get(index));
            }
        }
        return matches;
    }

    private static <T> void reorder(List<T> values, int[] order) {
        List<T> snapshot = new ArrayList<>(values);
        for (int index = 0; index < order.length; index++) {
            values.set(index, snapshot.get(order[index]));
        }
    }

    private static <T> double squaredDistance(
        double originX,
        double originY,
        double originZ,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter,
        T value
    ) {
        double dx = xGetter.get(value) - originX;
        double dy = yGetter.get(value) - originY;
        double dz = zGetter.get(value) - originZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double squaredDistanceAt(
        double originX,
        double originY,
        double originZ,
        double[] positions,
        int index
    ) {
        int offset = index * 3;
        double dx = positions[offset] - originX;
        double dy = positions[offset + 1] - originY;
        double dz = positions[offset + 2] - originZ;
        return dx * dx + dy * dy + dz * dz;
    }

}
