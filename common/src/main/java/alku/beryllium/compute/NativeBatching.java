package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;

/**
 * Shared policy for deciding when a Java-side batch is large enough to cross the FFM boundary.
 */
public final class NativeBatching {
    private static final int DEFAULT_ENTITY_BATCH_THRESHOLD = 32;
    private static final int DEFAULT_POTENTIAL_BATCH_THRESHOLD = 32;
    private static final int DEFAULT_CHUNK_SEND_SELECTION_THRESHOLD = 4096;
    private static final String ENTITY_BATCH_THRESHOLD_PROPERTY = "beryllium.native.entityBatchThreshold";
    private static final String POTENTIAL_BATCH_THRESHOLD_PROPERTY = "beryllium.native.potentialBatchThreshold";
    private static final String CHUNK_SEND_SELECTION_THRESHOLD_PROPERTY = "beryllium.native.chunkSendSelectionThreshold";

    private static final int ENTITY_BATCH_THRESHOLD = readPositiveIntProperty(
        ENTITY_BATCH_THRESHOLD_PROPERTY,
        DEFAULT_ENTITY_BATCH_THRESHOLD
    );
    private static final int POTENTIAL_BATCH_THRESHOLD = readPositiveIntProperty(
        POTENTIAL_BATCH_THRESHOLD_PROPERTY,
        DEFAULT_POTENTIAL_BATCH_THRESHOLD
    );
    private static final int CHUNK_SEND_SELECTION_THRESHOLD = readPositiveIntProperty(
        CHUNK_SEND_SELECTION_THRESHOLD_PROPERTY,
        DEFAULT_CHUNK_SEND_SELECTION_THRESHOLD
    );

    private NativeBatching() {
    }

    public static boolean shouldUseNativeEntityBatch(int candidateCount) {
        return candidateCount >= ENTITY_BATCH_THRESHOLD && NativeBridge.isLoaded();
    }

    public static boolean shouldUseNativePotentialBatch(int chargeCount) {
        return chargeCount >= POTENTIAL_BATCH_THRESHOLD && NativeBridge.isLoaded();
    }

    public static boolean shouldUseNativeChunkSendSelection(int candidateCount) {
        return candidateCount >= CHUNK_SEND_SELECTION_THRESHOLD && NativeBridge.isLoaded();
    }

    public static int entityBatchThreshold() {
        return ENTITY_BATCH_THRESHOLD;
    }

    public static int potentialBatchThreshold() {
        return POTENTIAL_BATCH_THRESHOLD;
    }

    public static int chunkSendSelectionThreshold() {
        return CHUNK_SEND_SELECTION_THRESHOLD;
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
