package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;

/**
 * Selects the distance-ordered subset used by PlayerChunkSender's partial batch path.
 */
public final class ChunkSendBatchSelector {
    private ChunkSendBatchSelector() {
    }

    public static int[] selectNearestChunkIndices(
        int originX,
        int originZ,
        long[] packedChunkPositions,
        int limit
    ) {
        if (packedChunkPositions == null) {
            throw new IllegalArgumentException("packedChunkPositions must not be null");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }

        int[] output = new int[Math.min(limit, packedChunkPositions.length)];
        selectNearestChunkIndices(originX, originZ, packedChunkPositions, limit, output);
        return output;
    }

    public static int selectNearestChunkIndices(
        int originX,
        int originZ,
        long[] packedChunkPositions,
        int limit,
        int[] output
    ) {
        if (packedChunkPositions != null
            && NativeBatching.shouldUseNativeChunkSendSelection(packedChunkPositions.length)) {
            return NativeBridge.selectNearestChunkIndices(
                originX,
                originZ,
                packedChunkPositions,
                limit,
                output
            );
        }
        return JavaComputeKernels.selectNearestChunkIndices(
            originX,
            originZ,
            packedChunkPositions,
            limit,
            output
        );
    }
}
