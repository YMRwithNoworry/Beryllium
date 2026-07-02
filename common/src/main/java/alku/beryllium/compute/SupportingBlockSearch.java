package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.Optional;

/**
 * Selects the nearest supporting block using the same distance and tie-break rules as vanilla.
 */
public final class SupportingBlockSearch {
    private SupportingBlockSearch() {
    }

    public static Optional<BlockPos> findNearest(
        Iterable<? extends BlockPos> candidates,
        double originX,
        double originY,
        double originZ
    ) {
        int[] positions = packPositions(candidates);
        if (positions.length == 0) {
            return Optional.empty();
        }

        int index = NativeBatching.shouldUseNativeEntityBatch(positions.length / 3)
            ? NativeBridge.findNearestBlockCenterIndex(originX, originY, originZ, positions)
            : JavaComputeKernels.findNearestBlockCenterIndex(originX, originY, originZ, positions);
        if (index < 0) {
            return Optional.empty();
        }

        int offset = index * 3;
        return Optional.of(new BlockPos(positions[offset], positions[offset + 1], positions[offset + 2]));
    }

    private static int[] packPositions(Iterable<? extends BlockPos> candidates) {
        int[] positions = new int[24];
        int offset = 0;
        for (BlockPos candidate : candidates) {
            if (offset + 3 > positions.length) {
                positions = Arrays.copyOf(positions, positions.length * 2);
            }
            positions[offset] = candidate.getX();
            positions[offset + 1] = candidate.getY();
            positions[offset + 2] = candidate.getZ();
            offset += 3;
        }

        return Arrays.copyOf(positions, offset);
    }
}
