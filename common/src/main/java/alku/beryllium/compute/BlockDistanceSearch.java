package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.Function;

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
}
