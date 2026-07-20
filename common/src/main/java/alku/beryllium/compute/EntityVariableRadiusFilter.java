package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Filters entity-like lists against a per-candidate inclusive squared radius.
 */
public final class EntityVariableRadiusFilter {
    private EntityVariableRadiusFilter() {
    }

    public static <T extends Entity> List<T> filterWithinInclusiveDistances(
        List<? extends T> entities,
        Entity origin,
        RadiusSquaredGetter<? super T> radiusSquaredGetter
    ) {
        return filterWithinInclusiveDistances(
            entities,
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            radiusSquaredGetter,
            Entity::getX,
            Entity::getY,
            Entity::getZ
        );
    }

    public static <T> List<T> filterWithinInclusiveDistances(
        List<? extends T> values,
        double originX,
        double originY,
        double originZ,
        RadiusSquaredGetter<? super T> radiusSquaredGetter,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter
    ) {
        if (values.isEmpty()) {
            return List.of();
        }

        if (!NativeBatching.shouldUseNativeVariableRadiusBatch(values.size())) {
            List<T> matches = new ArrayList<>();
            for (T value : values) {
                double radiusSquared = radiusSquaredGetter.get(value);
                if (radiusSquared < 0.0) {
                    throw new IllegalArgumentException("radiusSquared must be non-negative");
                }
                if (squaredDistance(originX, originY, originZ, xGetter, yGetter, zGetter, value) <= radiusSquared) {
                    matches.add(value);
                }
            }
            return matches;
        }

        double[] positions = EntityPacking.packPositions(values, xGetter, yGetter, zGetter);
        double[] radiiSquared = packRadii(values, radiusSquaredGetter);
        int[] matchingIndices = new int[values.size()];
        int matchCount = NativeBridge.filterWithinRadii(originX, originY, originZ, positions, radiiSquared, matchingIndices);
        List<T> matches = new ArrayList<>(matchCount);
        for (int matchIndex = 0; matchIndex < matchCount; matchIndex++) {
            matches.add(values.get(matchingIndices[matchIndex]));
        }
        return matches;
    }

    public static <T> Optional<T> findFirstWithinInclusiveDistancesAfterDistance(
        List<? extends T> values,
        double originX,
        double originY,
        double originZ,
        RadiusSquaredGetter<? super T> radiusSquaredGetter,
        Predicate<? super T> afterDistance,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter
    ) {
        if (values.isEmpty()) {
            return Optional.empty();
        }

        if (!NativeBatching.shouldUseNativeVariableRadiusBatch(values.size())) {
            for (T value : values) {
                double radiusSquared = radiusSquaredGetter.get(value);
                if (radiusSquared < 0.0) {
                    throw new IllegalArgumentException("radiusSquared must be non-negative");
                }
                if (squaredDistance(originX, originY, originZ, xGetter, yGetter, zGetter, value) <= radiusSquared
                    && afterDistance.test(value)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }

        double[] positions = EntityPacking.packPositions(values, xGetter, yGetter, zGetter);
        double[] radiiSquared = packRadii(values, radiusSquaredGetter);
        int[] matchingIndices = new int[values.size()];
        int matchCount = NativeBridge.filterWithinRadii(originX, originY, originZ, positions, radiiSquared, matchingIndices);
        for (int matchIndex = 0; matchIndex < matchCount; matchIndex++) {
            int index = matchingIndices[matchIndex];
            T value = values.get(index);
            if (afterDistance.test(value)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static <T> double[] packRadii(List<? extends T> values, RadiusSquaredGetter<? super T> radiusSquaredGetter) {
        double[] radiiSquared = new double[values.size()];
        for (int index = 0; index < values.size(); index++) {
            T value = values.get(index);
            double radiusSquared = radiusSquaredGetter.get(value);
            if (radiusSquared < 0.0) {
                throw new IllegalArgumentException("radiusSquared must be non-negative");
            }
            radiiSquared[index] = radiusSquared;
        }
        return radiiSquared;
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

    @FunctionalInterface
    public interface RadiusSquaredGetter<T> {
        double get(T value);
    }
}
