package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Sorts entity-like lists by squared distance while preserving vanilla stable tie order.
 */
public final class EntityDistanceSort {
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
        int[] order = NativeBridge.sortWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions);
        List<T> matches = new ArrayList<>(order.length);
        for (int index : order) {
            matches.add(values.get(index));
        }
        return matches;
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
        int[] order = NativeBridge.sortWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions);
        boolean[] withinRadius = new boolean[values.size()];
        for (int index : order) {
            withinRadius[index] = true;
        }

        boolean[] accepted = new boolean[values.size()];
        for (int index = 0; index < values.size(); index++) {
            if (withinRadius[index] && postDistancePredicate.test(values.get(index))) {
                accepted[index] = true;
            }
        }

        List<T> matches = new ArrayList<>(order.length);
        for (int index : order) {
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
}
