package alku.beryllium.compute;

public final class NativeBatchingVerifier {
    private NativeBatchingVerifier() {
    }

    public static void verifyDefaultThreshold() {
        if (NativeBatching.entityBatchThreshold() != 32) {
            throw new AssertionError("Native entity batch threshold mismatch, expected 32 but got " + NativeBatching.entityBatchThreshold());
        }
    }
}
