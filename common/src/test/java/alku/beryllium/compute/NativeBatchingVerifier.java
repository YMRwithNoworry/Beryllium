package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;

public final class NativeBatchingVerifier {
    private NativeBatchingVerifier() {
    }

    public static void verifyDefaultThreshold() {
        if (NativeBatching.entityBatchThreshold() != Integer.MAX_VALUE) {
            throw new AssertionError(
                "Native entity batch threshold mismatch, expected disabled default but got "
                    + NativeBatching.entityBatchThreshold()
            );
        }
        if (NativeBatching.entityDistanceSortThreshold() != 256) {
            throw new AssertionError(
                "Native entity distance sort threshold mismatch, expected 256 but got "
                    + NativeBatching.entityDistanceSortThreshold()
            );
        }
        if (NativeBatching.blockDistanceBatchThreshold() != Integer.MAX_VALUE) {
            throw new AssertionError(
                "Native block distance batch threshold mismatch, expected disabled default but got "
                    + NativeBatching.blockDistanceBatchThreshold()
            );
        }
        if (NativeBatching.potentialBatchThreshold() != 512) {
            throw new AssertionError("Native potential batch threshold mismatch, expected 512 but got " + NativeBatching.potentialBatchThreshold());
        }
        if (NativeBatching.chunkSendSelectionThreshold() != 8192) {
            throw new AssertionError(
                "Native chunk send selection threshold mismatch, expected 8192 but got "
                    + NativeBatching.chunkSendSelectionThreshold()
            );
        }
        if (NativeBatching.nearestItemTopKThreshold() != 1024) {
            throw new AssertionError(
                "Native nearest-item Top-K threshold mismatch, expected 1024 but got "
                + NativeBatching.nearestItemTopKThreshold()
            );
        }
        if (NativeBatching.variableRadiusBatchThreshold() != Integer.MAX_VALUE) {
            throw new AssertionError(
                "Native variable-radius threshold mismatch, expected disabled default but got "
                    + NativeBatching.variableRadiusBatchThreshold()
            );
        }
        if (NativeBatching.aabbBatchThreshold() != Integer.MAX_VALUE) {
            throw new AssertionError(
                "Native AABB threshold mismatch, expected disabled default but got "
                    + NativeBatching.aabbBatchThreshold()
            );
        }
        if (NativeBatching.shouldUseNativeVariableRadiusBatch(16_384)) {
            throw new AssertionError("Native variable-radius batch should remain disabled by default");
        }
        if (NativeBatching.shouldUseNativeAabbBatch(16_384)) {
            throw new AssertionError("Native AABB batch should remain disabled by default");
        }
        if (NativeBatching.shouldUseNativePotentialBatch(511)) {
            throw new AssertionError("Native potential batch should not activate below its threshold");
        }
        if (NativeBatching.shouldUseNativeBlockDistanceBatch(65_536)) {
            throw new AssertionError("Native block distance batch should remain disabled by default");
        }
        if (NativeBatching.shouldUseNativeEntityBatch(16_384)) {
            throw new AssertionError("Native entity distance batches should remain disabled by default");
        }
        if (NativeBatching.shouldUseNativeEntityDistanceSort(255)) {
            throw new AssertionError("Native entity distance sort should remain disabled below its threshold");
        }
        if (NativeBatching.shouldUseNativeEntityDistanceSort(256) != NativeBridge.isLoaded()) {
            throw new AssertionError("Native entity distance sort activation should follow native availability at its threshold");
        }
        if (NativeBatching.shouldUseNativePotentialBatch(512) != NativeBridge.isLoaded()) {
            throw new AssertionError("Native potential batch activation should follow native availability at its threshold");
        }
        if (NativeBatching.shouldUseNativeChunkSendSelection(127)) {
            throw new AssertionError("Native chunk send selection should remain disabled below its threshold");
        }
        if (NativeBatching.shouldUseNativeChunkSendSelection(8191)) {
            throw new AssertionError("Native chunk send selection should remain disabled below its threshold");
        }
        if (NativeBatching.shouldUseNativeChunkSendSelection(8192) != NativeBridge.isLoaded()) {
            throw new AssertionError("Native chunk send selection activation should follow native availability at its threshold");
        }
        if (NativeBatching.shouldUseNativeNearestItemTopK(1023)) {
            throw new AssertionError("Native nearest-item Top-K should not activate below its threshold");
        }
        if (NativeBatching.shouldUseNativeNearestItemTopK(1024) != NativeBridge.isLoaded()) {
            throw new AssertionError("Native nearest-item Top-K activation should follow native availability at its threshold");
        }
    }
}
