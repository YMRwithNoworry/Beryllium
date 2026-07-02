package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Finds the first entity-like value matching ordered predicates with a batched distance gate.
 */
public final class EntityDistancePredicateSearch {
    private EntityDistancePredicateSearch() {
    }

    public static <T extends Entity> Optional<T> findFirstWithinExclusiveDistance(
        List<? extends T> entities,
        Entity origin,
        double radius,
        Predicate<? super T> beforeDistance,
        Predicate<? super T> afterDistance
    ) {
        if (radius < 0.0) {
            throw new IllegalArgumentException("radius must be non-negative");
        }

        return findFirstWithinExclusiveDistance(
            entities,
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            radius * radius,
            beforeDistance,
            afterDistance,
            Entity::getX,
            Entity::getY,
            Entity::getZ
        );
    }

    public static <T extends Entity> Optional<T> findFirstWithinInclusiveDistance(
        List<? extends T> entities,
        Entity origin,
        double radius,
        Predicate<? super T> beforeDistance,
        Predicate<? super T> afterDistance
    ) {
        if (radius < 0.0) {
            throw new IllegalArgumentException("radius must be non-negative");
        }

        return findFirstWithinInclusiveDistance(
            entities,
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            radius * radius,
            beforeDistance,
            afterDistance,
            Entity::getX,
            Entity::getY,
            Entity::getZ
        );
    }

    public static <T> Optional<T> findFirstWithinExclusiveDistance(
        List<? extends T> values,
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        Predicate<? super T> beforeDistance,
        Predicate<? super T> afterDistance,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter
    ) {
        return findFirstWithinDistance(
            values,
            originX,
            originY,
            originZ,
            radiusSquared,
            beforeDistance,
            afterDistance,
            xGetter,
            yGetter,
            zGetter,
            false
        );
    }

    public static <T> Optional<T> findFirstWithinInclusiveDistance(
        List<? extends T> values,
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        Predicate<? super T> beforeDistance,
        Predicate<? super T> afterDistance,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter
    ) {
        return findFirstWithinDistance(
            values,
            originX,
            originY,
            originZ,
            radiusSquared,
            beforeDistance,
            afterDistance,
            xGetter,
            yGetter,
            zGetter,
            true
        );
    }

    private static <T> Optional<T> findFirstWithinDistance(
        List<? extends T> values,
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        Predicate<? super T> beforeDistance,
        Predicate<? super T> afterDistance,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter,
        boolean includeBoundary
    ) {
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }

        int[] distanceMatches = null;
        if (NativeBatching.shouldUseNativeEntityBatch(values.size())) {
            double[] positions = EntityPacking.packPositions(values, xGetter, yGetter, zGetter);
            distanceMatches = includeBoundary
                ? NativeBridge.filterWithinRadius(originX, originY, originZ, radiusSquared, positions)
                : NativeBridge.filterWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions);
        }

        int distanceMatchCursor = 0;
        for (int index = 0; index < values.size(); index++) {
            T value = values.get(index);
            if (!beforeDistance.test(value)) {
                continue;
            }

            if (distanceMatches != null) {
                distanceMatchCursor = advanceDistanceMatchCursor(distanceMatches, distanceMatchCursor, index);
            }
            if (!isWithinDistance(distanceMatches, distanceMatchCursor, index, originX, originY, originZ, radiusSquared, xGetter, yGetter, zGetter, value, includeBoundary)) {
                continue;
            }

            if (afterDistance.test(value)) {
                return Optional.of(value);
            }
        }

        return Optional.empty();
    }

    private static <T> boolean isWithinDistance(
        int[] distanceMatches,
        int distanceMatchCursor,
        int index,
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter,
        T value,
        boolean includeBoundary
    ) {
        if (distanceMatches == null) {
            double distance = squaredDistance(originX, originY, originZ, xGetter, yGetter, zGetter, value);
            return includeBoundary ? distance <= radiusSquared : distance < radiusSquared;
        }

        return distanceMatchCursor < distanceMatches.length && distanceMatches[distanceMatchCursor] == index;
    }

    private static int advanceDistanceMatchCursor(int[] distanceMatches, int cursor, int index) {
        while (cursor < distanceMatches.length && distanceMatches[cursor] < index) {
            cursor++;
        }
        return cursor;
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
}
