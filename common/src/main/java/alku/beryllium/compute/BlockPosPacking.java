package alku.beryllium.compute;

import net.minecraft.core.BlockPos;

/**
 * Mirrors Minecraft's compact BlockPos long layout without allocating a BlockPos while decoding.
 */
final class BlockPosPacking {
    private static final int MIN_XZ = -(1 << 25);
    private static final int MAX_XZ = (1 << 25) - 1;
    private static final int MIN_Y = -(1 << 11);
    private static final int MAX_Y = (1 << 11) - 1;

    private BlockPosPacking() {
    }

    static boolean isLossless(BlockPos position) {
        return position.getX() >= MIN_XZ && position.getX() <= MAX_XZ
            && position.getY() >= MIN_Y && position.getY() <= MAX_Y
            && position.getZ() >= MIN_XZ && position.getZ() <= MAX_XZ;
    }

    static int unpackX(long packedPosition) {
        return (int) (packedPosition >> 38);
    }

    static int unpackY(long packedPosition) {
        return (int) (packedPosition << 52 >> 52);
    }

    static int unpackZ(long packedPosition) {
        return (int) (packedPosition << 26 >> 38);
    }
}
