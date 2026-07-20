package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;

/**
 * Shared policy for deciding when a Java-side batch is large enough to cross the FFM boundary.
 */
public final class NativeBatching {
    private static final int DEFAULT_ENTITY_BATCH_THRESHOLD = 32;
    private static final int DEFAULT_POTENTIAL_BATCH_THRESHOLD = 32;
    private static final int DEFAULT_CHUNK_SEND_SELECTION_THRESHOLD = 4096;
    private static final int DEFAULT_NEAREST_ITEM_TOP_K_THRESHOLD = 1024;
    // FFM array copies currently outweigh these primitive filters without an explicit deployment profile.
    private static final int DEFAULT_VARIABLE_RADIUS_BATCH_THRESHOLD = Integer.MAX_VALUE;
    private static final int DEFAULT_AABB_BATCH_THRESHOLD = Integer.MAX_VALUE;
    private static final String ENTITY_BATCH_THRESHOLD_PROPERTY = "beryllium.native.entityBatchThreshold";
    private static final String POTENTIAL_BATCH_THRESHOLD_PROPERTY = "beryllium.native.potentialBatchThreshold";
    private static final String CHUNK_SEND_SELECTION_THRESHOLD_PROPERTY = "beryllium.native.chunkSendSelectionThreshold";
    private static final String NEAREST_ITEM_TOP_K_THRESHOLD_PROPERTY = "beryllium.native.nearestItemTopKThreshold";
    private static final String VARIABLE_RADIUS_BATCH_THRESHOLD_PROPERTY = "beryllium.native.variableRadiusBatchThreshold";
    private static final String AABB_BATCH_THRESHOLD_PROPERTY = "beryllium.native.aabbBatchThreshold";

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
    private static final int NEAREST_ITEM_TOP_K_THRESHOLD = readPositiveIntProperty(
        NEAREST_ITEM_TOP_K_THRESHOLD_PROPERTY,
        DEFAULT_NEAREST_ITEM_TOP_K_THRESHOLD
    );
    private static final int VARIABLE_RADIUS_BATCH_THRESHOLD = readPositiveIntProperty(
        VARIABLE_RADIUS_BATCH_THRESHOLD_PROPERTY,
        DEFAULT_VARIABLE_RADIUS_BATCH_THRESHOLD
    );
    private static final int AABB_BATCH_THRESHOLD = readPositiveIntProperty(
        AABB_BATCH_THRESHOLD_PROPERTY,
        DEFAULT_AABB_BATCH_THRESHOLD
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

    public static boolean shouldUseNativeNearestItemTopK(int candidateCount) {
        return candidateCount >= NEAREST_ITEM_TOP_K_THRESHOLD && NativeBridge.isLoaded();
    }

    public static boolean shouldUseNativeVariableRadiusBatch(int candidateCount) {
        return candidateCount >= VARIABLE_RADIUS_BATCH_THRESHOLD && NativeBridge.isLoaded();
    }

    public static boolean shouldUseNativeAabbBatch(int candidateCount) {
        return candidateCount >= AABB_BATCH_THRESHOLD && NativeBridge.isLoaded();
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

    public static int nearestItemTopKThreshold() {
        return NEAREST_ITEM_TOP_K_THRESHOLD;
    }

    public static int variableRadiusBatchThreshold() {
        return VARIABLE_RADIUS_BATCH_THRESHOLD;
    }

    public static int aabbBatchThreshold() {
        return AABB_BATCH_THRESHOLD;
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
