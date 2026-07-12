package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;

/**
 * Shared policy for deciding when a Java-side batch is large enough to cross the FFM boundary.
 */
public final class NativeBatching {
    private static final int DEFAULT_ENTITY_BATCH_THRESHOLD = 32;
    private static final String ENTITY_BATCH_THRESHOLD_PROPERTY = "beryllium.native.entityBatchThreshold";

    private static final int ENTITY_BATCH_THRESHOLD = readPositiveIntProperty(
        ENTITY_BATCH_THRESHOLD_PROPERTY,
        DEFAULT_ENTITY_BATCH_THRESHOLD
    );

    private NativeBatching() {
    }

    public static boolean shouldUseNativeEntityBatch(int candidateCount) {
        return candidateCount >= ENTITY_BATCH_THRESHOLD && NativeBridge.isLoaded();
    }

    public static int entityBatchThreshold() {
        return ENTITY_BATCH_THRESHOLD;
    }

    private static int readPositiveIntProperty(String property, int fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
