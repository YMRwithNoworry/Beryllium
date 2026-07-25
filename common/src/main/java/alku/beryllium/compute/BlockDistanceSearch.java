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

        if (!NativeBatching.shouldUseNativeBlockDistanceBatch(values.size())) {
            return findNearestByDistanceJava(values, origin, positionGetter);
        }

        PackedNearestPositions positions = packNearestPositions(values, positionGetter);
        int nearestIndex = positions.compactPositions() != null
            ? NativeBridge.findNearestPackedBlockCornerIndex(
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                positions.compactPositions()
            )
            : NativeBridge.findNearestBlockCornerIndex(
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                positions.expandedPositions()
            );

        return nearestIndex >= 0 ? values.get(nearestIndex) : null;
    }

    public static <T> T findNearestByDistanceWithinInclusiveRadius(
        List<T> values,
        BlockPos origin,
        int radius,
        Function<? super T, BlockPos> positionGetter
    ) {
        if (values.isEmpty()) {
            return null;
        }

        int radiusSquared = radius * radius;
        if (radiusSquared < 0) {
            return null;
        }

        if (!NativeBatching.shouldUseNativeBlockDistanceBatch(values.size())) {
            return findNearestByDistanceWithinInclusiveRadiusJava(values, origin, radiusSquared, positionGetter);
        }

        PackedNearestPositions positions = packNearestPositions(values, positionGetter);
        int nearestIndex = positions.compactPositions() != null
            ? NativeBridge.findNearestPackedBlockCornerIndexWithinRadius(
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                radiusSquared,
                positions.compactPositions()
            )
            : NativeBridge.findNearestBlockCornerIndexWithinRadius(
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                radiusSquared,
                positions.expandedPositions()
            );

        return nearestIndex >= 0 ? values.get(nearestIndex) : null;
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
        int[] matchingIndices = new int[values.size()];
        int matchCount = NativeBatching.shouldUseNativeBlockDistanceBatch(values.size())
            ? NativeBridge.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions, matchingIndices)
            : JavaComputeKernels.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions, matchingIndices);

        T nearest = null;
        double nearestDistance = 0.0;
        for (int cursor = 0; cursor < matchCount; cursor++) {
            int index = matchingIndices[cursor];
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
        int[] matchingIndices = new int[values.size()];
        int matchCount = NativeBatching.shouldUseNativeBlockDistanceBatch(values.size())
            ? NativeBridge.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions, matchingIndices)
            : JavaComputeKernels.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions, matchingIndices);

        if (matchCount == 0) {
            return List.of();
        }

        List<T> result = new ArrayList<>(matchCount);
        for (int cursor = 0; cursor < matchCount; cursor++) {
            int index = matchingIndices[cursor];
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
        int[] matchingIndices = new int[values.size()];
        int matchCount = NativeBatching.shouldUseNativeBlockDistanceBatch(values.size())
            ? NativeBridge.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions, matchingIndices)
            : JavaComputeKernels.filterWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions, matchingIndices);

        for (int cursor = 0; cursor < matchCount; cursor++) {
            int index = matchingIndices[cursor];
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
        return NativeBatching.shouldUseNativeBlockDistanceBatch(values.size())
            ? NativeBridge.countWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions)
            : JavaComputeKernels.countWithinRadius(origin.getX(), origin.getY(), origin.getZ(), radiusSquared, positions);
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
            writePosition(positions, index, position);
        }
        return positions;
    }

    private static <T> PackedNearestPositions packNearestPositions(
        List<T> values,
        Function<? super T, BlockPos> positionGetter
    ) {
        long[] compactPositions = new long[values.size()];
        for (int index = 0; index < values.size(); index++) {
            BlockPos position = positionGetter.apply(values.get(index));
            if (BlockPosPacking.isLossless(position)) {
                compactPositions[index] = position.asLong();
                continue;
            }

            int[] expandedPositions = new int[values.size() * 3];
            for (int previousIndex = 0; previousIndex < index; previousIndex++) {
                writePackedPosition(expandedPositions, previousIndex, compactPositions[previousIndex]);
            }
            writePosition(expandedPositions, index, position);
            for (int remainingIndex = index + 1; remainingIndex < values.size(); remainingIndex++) {
                writePosition(expandedPositions, remainingIndex, positionGetter.apply(values.get(remainingIndex)));
            }
            return new PackedNearestPositions(null, expandedPositions);
        }

        return new PackedNearestPositions(compactPositions, null);
    }

    private static <T> T findNearestByDistanceJava(
        List<T> values,
        BlockPos origin,
        Function<? super T, BlockPos> positionGetter
    ) {
        T nearest = null;
        double nearestDistance = 0.0;
        for (T value : values) {
            BlockPos position = positionGetter.apply(value);
            double distance = squaredDistance(origin, position);
            if (nearest == null || distance < nearestDistance) {
                nearest = value;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    private static <T> T findNearestByDistanceWithinInclusiveRadiusJava(
        List<T> values,
        BlockPos origin,
        long radiusSquared,
        Function<? super T, BlockPos> positionGetter
    ) {
        T nearest = null;
        double nearestDistance = 0.0;
        for (T value : values) {
            BlockPos position = positionGetter.apply(value);
            if (squaredDistanceForRadius(origin, position) > radiusSquared) {
                continue;
            }

            double distance = squaredDistance(origin, position);
            if (nearest == null || distance < nearestDistance) {
                nearest = value;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    private static void writePosition(int[] positions, int index, BlockPos position) {
        int offset = index * 3;
        positions[offset] = position.getX();
        positions[offset + 1] = position.getY();
        positions[offset + 2] = position.getZ();
    }

    private static void writePackedPosition(int[] positions, int index, long packedPosition) {
        int offset = index * 3;
        positions[offset] = BlockPosPacking.unpackX(packedPosition);
        positions[offset + 1] = BlockPosPacking.unpackY(packedPosition);
        positions[offset + 2] = BlockPosPacking.unpackZ(packedPosition);
    }

    private static double squaredDistance(BlockPos origin, BlockPos position) {
        double dx = (double) position.getX() - origin.getX();
        double dy = (double) position.getY() - origin.getY();
        double dz = (double) position.getZ() - origin.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static long squaredDistanceForRadius(BlockPos origin, BlockPos position) {
        long dx = (long) position.getX() - origin.getX();
        long dy = (long) position.getY() - origin.getY();
        long dz = (long) position.getZ() - origin.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static double squaredDistanceAt(BlockPos origin, int[] positions, int index) {
        int offset = index * 3;
        double dx = (double) positions[offset] - origin.getX();
        double dy = (double) positions[offset + 1] - origin.getY();
        double dz = (double) positions[offset + 2] - origin.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private record PackedNearestPositions(long[] compactPositions, int[] expandedPositions) {
    }
}
