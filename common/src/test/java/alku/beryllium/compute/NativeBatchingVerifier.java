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
        if (NativeBatching.shouldUseNativePotentialBatch(31)) {
            throw new AssertionError("Native potential batch should not activate below its threshold");
        }
        if (NativeBatching.shouldUseNativePotentialBatch(32) != NativeBridge.isLoaded()) {
            throw new AssertionError("Native potential batch activation should follow native availability at its threshold");
        }
    }
}
