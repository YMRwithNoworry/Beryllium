package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;

public final class NativeBatchingVerifier {
    private NativeBatchingVerifier() {
    }

    public static void verifyDefaultThreshold() {
        if (NativeBatching.entityBatchThreshold() != 32) {
            throw new AssertionError("Native entity batch threshold mismatch, expected 32 but got " + NativeBatching.entityBatchThreshold());
        }
        if (NativeBatching.potentialBatchThreshold() != 32) {
            throw new AssertionError("Native potential batch threshold mismatch, expected 32 but got " + NativeBatching.potentialBatchThreshold());
        }
        if (NativeBatching.chunkSendSelectionThreshold() != 4096) {
            throw new AssertionError(
                "Native chunk send selection threshold mismatch, expected 4096 but got "
                    + NativeBatching.chunkSendSelectionThreshold()
            );
        }
        if (NativeBatching.nearestItemTopKThreshold() != 1024) {
            throw new AssertionError(
                "Native nearest-item Top-K threshold mismatch, expected 1024 but got "
                + NativeBatching.nearestItemTopKThreshold()
            );
        }
        if (NativeBatching.shouldUseNativePotentialBatch(31)) {
            throw new AssertionError("Native potential batch should not activate below its threshold");
        }
        if (NativeBatching.shouldUseNativePotentialBatch(32) != NativeBridge.isLoaded()) {
            throw new AssertionError("Native potential batch activation should follow native availability at its threshold");
        }
        if (NativeBatching.shouldUseNativeChunkSendSelection(4095)) {
            throw new AssertionError("Native chunk send selection should not activate below its threshold");
        }
        if (NativeBatching.shouldUseNativeChunkSendSelection(4096) != NativeBridge.isLoaded()) {
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
