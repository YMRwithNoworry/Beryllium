package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Filters entity-like lists by exclusive squared radius, matching Entity.closerThan semantics.
 */
public final class EntityDistanceFilter {
    private EntityDistanceFilter() {
    }

    public static <T extends Entity> List<T> filterWithinExclusiveDistance(List<? extends T> entities, Entity origin, double radius) {
        return filterWithinExclusiveDistance(
            entities,
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            radius,
            Entity::getX,
            Entity::getY,
            Entity::getZ
        );
    }

    public static <T> List<T> filterWithinExclusiveDistance(
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
            return matches;
        }

        double[] positions = EntityPacking.packPositions(values, xGetter, yGetter, zGetter);
        int[] matchIndices = NativeBridge.filterWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions);
        List<T> matches = new ArrayList<>(matchIndices.length);
        for (int index : matchIndices) {
            matches.add(values.get(index));
        }
        return matches;
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
