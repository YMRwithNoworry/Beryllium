package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Finds block-like values by squared distance to BlockPos low corners while preserving encounter-order ties.
 */
public final class BlockDistanceSearch {
    private BlockDistanceSearch() {
    }

    public static <T> T findNearestByDistance(
        List<T> values,
        BlockPos origin,
        Function<? super T, BlockPos> positionGetter
    ) {
        if (values.isEmpty()) {
            return null;
        }

        int[] positions = packPositions(values, positionGetter);
        int nearestIndex = NativeBatching.shouldUseNativeEntityBatch(values.size())
            ? NativeBridge.findNearestBlockCornerIndex(origin.getX(), origin.getY(), origin.getZ(), positions)
            : JavaComputeKernels.findNearestBlockCornerIndex(origin.getX(), origin.getY(), origin.getZ(), positions);

        return nearestIndex >= 0 ? values.get(nearestIndex) : null;
    }

    public static <T> T findNearestByDistanceWithinInclusiveRadius(
        List<T> values,
        BlockPos origin,
        int radius,
        Function<? super T, BlockPos> positionGetter
    ) {
        return findNearestByDistanceWithinInclusiveRadius(values, origin, radius, positionGetter, value -> true);
    }

    public static <T> T findNearestByDistanceWithinInclusiveRadius(
        List<T> values,
        BlockPos origin,
        int radius,
        Function<? super T, BlockPos> positionGetter,
        Predicate<? super T> afterDistancePredicate
    ) {
        if (values.isEmpty()) {
            return null;
        }

        int radiusSquared = radius * radius;
        if (radiusSquared < 0) {
            return null;
        }

        int[] positions = packPositions(values, positionGetter);
        int[] matchingIndices = NativeBatching.shouldUseNativeEntityBatch(values.size())
            ? NativeBridge.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions)
            : JavaComputeKernels.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions);

        T nearest = null;
        double nearestDistance = 0.0;
        for (int index : matchingIndices) {
            T value = values.get(index);
            if (!afterDistancePredicate.test(value)) {
                continue;
            }

            double distance = squaredDistanceAt(origin, positions, index);
            if (nearest == null || distance < nearestDistance) {
                nearest = value;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    public static <T> List<T> filterByDistanceWithinInclusiveRadius(
        List<T> values,
        BlockPos origin,
        int radius,
        Function<? super T, BlockPos> positionGetter,
        Predicate<? super T> afterDistancePredicate
    ) {
        if (values.isEmpty()) {
            return List.of();
        }

        int radiusSquared = radius * radius;
        if (radiusSquared < 0) {
            return List.of();
        }

        int[] positions = packPositions(values, positionGetter);
        int[] matchingIndices = NativeBatching.shouldUseNativeEntityBatch(values.size())
            ? NativeBridge.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions)
            : JavaComputeKernels.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions);

        if (matchingIndices.length == 0) {
            return List.of();
        }

        List<T> result = new ArrayList<>(matchingIndices.length);
        for (int index : matchingIndices) {
            T value = values.get(index);
            if (afterDistancePredicate.test(value)) {
                result.add(value);
            }
        }

        return result;
    }

    public static <T> T findFirstByDistanceWithinInclusiveRadius(
        List<T> values,
        BlockPos origin,
        int radius,
        Function<? super T, BlockPos> positionGetter,
        Predicate<? super T> afterDistancePredicate
    ) {
        if (values.isEmpty()) {
            return null;
        }

        int radiusSquared = radius * radius;
        if (radiusSquared < 0) {
            return null;
        }

        int[] positions = packPositions(values, positionGetter);
        int[] matchingIndices = NativeBatching.shouldUseNativeEntityBatch(values.size())
            ? NativeBridge.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions)
            : JavaComputeKernels.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions);

        for (int index : matchingIndices) {
            T value = values.get(index);
            if (afterDistancePredicate.test(value)) {
                return value;
            }
        }

        return null;
    }

    public static <T> long countByDistanceWithinInclusiveRadius(
        List<T> values,
        BlockPos origin,
        int radius,
        Function<? super T, BlockPos> positionGetter
    ) {
        if (values.isEmpty()) {
            return 0L;
        }

        int radiusSquared = radius * radius;
        if (radiusSquared < 0) {
            return 0L;
        }

        int[] positions = packPositions(values, positionGetter);
        int[] matchingIndices = NativeBatching.shouldUseNativeEntityBatch(values.size())
            ? NativeBridge.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions)
            : JavaComputeKernels.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions);

        return matchingIndices.length;
    }

    public static <T> BlockPos findNearestPositionByDistance(
        Iterable<? extends T> values,
        BlockPos origin,
        Function<? super T, BlockPos> positionGetter,
        Predicate<? super BlockPos> positionPredicate
    ) {
        List<BlockPos> positions = new ArrayList<>();
        for (T value : values) {
            BlockPos position = positionGetter.apply(value);
            if (positionPredicate.test(position)) {
                positions.add(position);
            }
        }

        return findNearestByDistance(positions, origin, position -> position);
    }

    public static <T> BlockPos findNearestPositionByDistanceWithinInclusiveRadius(
        Iterable<? extends T> values,
        BlockPos origin,
        int radius,
        Function<? super T, BlockPos> positionGetter,
        Predicate<? super BlockPos> positionPredicate
    ) {
        List<BlockPos> positions = new ArrayList<>();
        for (T value : values) {
            positions.add(positionGetter.apply(value));
        }

        return findNearestByDistanceWithinInclusiveRadius(
            positions,
            origin,
            radius,
            position -> position,
            positionPredicate
        );
    }

    private static <T> int[] packPositions(List<T> values, Function<? super T, BlockPos> positionGetter) {
        int[] positions = new int[values.size() * 3];
        for (int index = 0; index < values.size(); index++) {
            BlockPos position = positionGetter.apply(values.get(index));
            int offset = index * 3;
            positions[offset] = position.getX();
            positions[offset + 1] = position.getY();
            positions[offset + 2] = position.getZ();
        }
        return positions;
    }

    private static double squaredDistanceAt(BlockPos origin, int[] positions, int index) {
        int offset = index * 3;
        double dx = (double) positions[offset] - origin.getX();
        double dy = (double) positions[offset + 1] - origin.getY();
        double dz = (double) positions[offset + 2] - origin.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
