package alku.beryllium.bridge;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Java 21 Foreign Function & Memory bridge.
 *
 * The FFM API is a preview API on the Java version supported by Minecraft
 * 1.21.1. Reflection keeps this common module compilable without preview
 * types while still using native downcalls at runtime.
 */
final class FfmNativeBridge {
    private static final int FFM_ERROR = NativeStatus.FFM_ERROR.code();
    private static final int CUBECL_MIN_CHARGE_COUNT = 262_144;
    private static final Object POTENTIAL_CACHE_UPDATE_LOCK = new Object();
    private static final AtomicLong NEXT_SESSION_ID = new AtomicLong();
    private static final AtomicLong POTENTIAL_CACHE_GENERATION = new AtomicLong();
    private static volatile Runtime runtime;

    private FfmNativeBridge() {
    }

    static synchronized boolean initialize() {
        if (runtime != null) {
            return true;
        }

        try {
            runtime = new Runtime();
            return true;
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError error) {
                throw error;
            }
            runtime = null;
            return false;
        }
    }

    static boolean isAvailable() {
        return runtime != null;
    }

    static long sessionIdForCurrentThread() {
        Runtime current = runtime;
        return current == null ? 0L : current.sessionIdForCurrentThread();
    }

    static int computeSquaredDistances(int originX, int originY, int originZ, int[] positions, long[] output) {
        return withStatusSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.INT);
            Buffer outputBuffer = session.output(output, Kind.LONG);
            int result = session.invoke(
                Function.COMPUTE_SQUARED_DISTANCES,
                originX,
                originY,
                originZ,
                positionsBuffer,
                outputBuffer
            );
            if (result == NativeStatus.OK.code()) {
                session.copyOutputs();
            }
            return result;
        });
    }

    static int selectNearestChunkIndices(
        int originX,
        int originZ,
        long[] packedChunkPositions,
        int limit,
        int[] output
    ) {
        return selectNearestChunkIndices(
            originX,
            originZ,
            packedChunkPositions,
            packedChunkPositions.length,
            limit,
            output,
            Math.min(limit, packedChunkPositions.length)
        );
    }

    static int selectNearestChunkIndices(
        int originX,
        int originZ,
        long[] packedChunkPositions,
        int positionsLength,
        int limit,
        int[] output,
        int outputLength
    ) {
        return withSession(session -> {
            Buffer positionsBuffer = session.input(packedChunkPositions, Kind.LONG, positionsLength);
            Buffer outputBuffer = session.uninitializedOutput(output, Kind.INT, outputLength);
            int result = session.invokeSelectNearestChunkIndices(
                originX,
                originZ,
                positionsBuffer,
                limit,
                outputBuffer
            );
            int expectedCount = Math.min(limit, positionsLength);
            if (result == expectedCount) {
                session.copyOutput(outputBuffer, result);
            }
            return result;
        });
    }

    static int computeSquaredDistances(
        double originX,
        double originY,
        double originZ,
        double[] positions,
        double[] output
    ) {
        return withStatusSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.DOUBLE);
            Buffer outputBuffer = session.output(output, Kind.DOUBLE);
            int result = session.invoke(
                Function.COMPUTE_SQUARED_DISTANCES_DOUBLE,
                originX,
                originY,
                originZ,
                positionsBuffer,
                outputBuffer
            );
            if (result == NativeStatus.OK.code()) {
                session.copyOutputs();
            }
            return result;
        });
    }

    static int computePotentialEnergyChange(
        int originX,
        int originY,
        int originZ,
        int[] positions,
        double[] charges,
        double chargeMultiplier,
        double[] output
    ) {
        return withStatusSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.INT);
            Buffer chargesBuffer = session.input(charges, Kind.DOUBLE);
            Buffer outputBuffer = session.uninitializedOutput(output, Kind.DOUBLE, 1);
            int result = session.invokeComputePotentialEnergyChange(
                originX,
                originY,
                originZ,
                positionsBuffer,
                chargesBuffer,
                chargeMultiplier,
                outputBuffer
            );
            if (result == NativeStatus.OK.code()) {
                session.copyOutput(outputBuffer, 1);
            }
            return result;
        });
    }

    static int setPotentialCharges(int[] positions, double[] charges) {
        long generation;
        int status;
        synchronized (POTENTIAL_CACHE_UPDATE_LOCK) {
            generation = POTENTIAL_CACHE_GENERATION.incrementAndGet();
            status = setPotentialChargesNative(positions, charges);
        }

        if (status == NativeStatus.OK.code()
            && charges.length >= CUBECL_MIN_CHARGE_COUNT
            && !NativeLibraryLoader.isCubeclPreviewLoaded()
            && NativeLibraryLoader.hasCubeclPreviewCandidate()) {
            scheduleCubeclPreviewLoad(generation, positions.clone(), charges.clone());
        }
        return status;
    }

    static int cubeclPreviewStatus() {
        return withSession(session -> session.invoke(Function.POTENTIAL_CUBECL_STATUS));
    }

    private static int setPotentialChargesNative(int[] positions, double[] charges) {
        return withStatusSession(session -> {
            Buffer posBuf = session.input(positions, Kind.INT);
            Buffer chgBuf = session.input(charges, Kind.DOUBLE);
            return session.invokeSetPotentialCharges(posBuf, chgBuf);
        });
    }

    private static void scheduleCubeclPreviewLoad(long generation, int[] positions, double[] charges) {
        Thread loader = new Thread(() -> {
            if (!NativeLibraryLoader.tryLoadCubeclPreview()) {
                return;
            }
            synchronized (POTENTIAL_CACHE_UPDATE_LOCK) {
                if (POTENTIAL_CACHE_GENERATION.get() == generation && runtime != null) {
                    setPotentialChargesNative(positions, charges);
                }
            }
        }, "beryllium-cubecl-loader");
        loader.setDaemon(true);
        loader.start();
    }

    static int computePotentialEnergyChangeCached(
        int originX, int originY, int originZ, double chargeMultiplier, double[] output
    ) {
        return withStatusSession(session -> {
            Buffer outputBuffer = session.uninitializedOutput(output, Kind.DOUBLE, 1);
            int result = session.invokeComputePotentialEnergyChangeCached(
                originX, originY, originZ, chargeMultiplier, outputBuffer
            );
            if (result == NativeStatus.OK.code()) {
                session.copyOutput(outputBuffer, 1);
            }
            return result;
        });
    }


    static int filterWithinRadius(
        int originX,
        int originY,
        int originZ,
        long radiusSquared,
        int[] positions,
        int[] output
    ) {
        return withSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.INT);
            Buffer outputBuffer = session.output(output, Kind.INT);
            int result = session.invoke(
                Function.FILTER_WITHIN_RADIUS,
                originX,
                originY,
                originZ,
                radiusSquared,
                positionsBuffer,
                outputBuffer
            );
            if (isValidCount(result, output.length)) {
                session.copyOutputs();
            }
            return result;
        });
    }

    static int countWithinRadius(
        int originX,
        int originY,
        int originZ,
        long radiusSquared,
        int[] positions
    ) {
        return withSession(session -> session.invoke(
            Function.COUNT_WITHIN_RADIUS,
            originX,
            originY,
            originZ,
            radiusSquared,
            session.input(positions, Kind.INT)
        ));
    }

    static int filterWithinRadius(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    ) {
        return withSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.DOUBLE);
            Buffer outputBuffer = session.output(output, Kind.INT);
            int result = session.invoke(
                Function.FILTER_WITHIN_RADIUS_DOUBLE,
                originX,
                originY,
                originZ,
                radiusSquared,
                positionsBuffer,
                outputBuffer
            );
            if (isValidCount(result, output.length)) {
                session.copyOutputs();
            }
            return result;
        });
    }

    static int filterWithinRadiusExclusive(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    ) {
        return withSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.DOUBLE);
            Buffer outputBuffer = session.output(output, Kind.INT);
            int result = session.invoke(
                Function.FILTER_WITHIN_RADIUS_EXCLUSIVE_DOUBLE,
                originX,
                originY,
                originZ,
                radiusSquared,
                positionsBuffer,
                outputBuffer
            );
            if (isValidCount(result, output.length)) {
                session.copyOutputs();
            }
            return result;
        });
    }

    static int filterWithinExclusiveChunkDistance(
        double originX,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    ) {
        return withSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.DOUBLE);
            Buffer outputBuffer = session.output(output, Kind.INT);
            int result = session.invoke(
                Function.FILTER_WITHIN_EXCLUSIVE_CHUNK_DISTANCE,
                originX,
                originZ,
                radiusSquared,
                positionsBuffer,
                outputBuffer
            );
            if (isValidCount(result, output.length)) {
                session.copyOutputs();
            }
            return result;
        });
    }

    static int filterWithinRadii(
        double originX,
        double originY,
        double originZ,
        double[] positions,
        double[] radiiSquared,
        int[] output
    ) {
        return withSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.DOUBLE);
            Buffer radiiBuffer = session.input(radiiSquared, Kind.DOUBLE);
            Buffer outputBuffer = session.output(output, Kind.INT);
            int result = session.invoke(
                Function.FILTER_WITHIN_RADII_DOUBLE,
                originX,
                originY,
                originZ,
                positionsBuffer,
                radiiBuffer,
                outputBuffer
            );
            if (isValidCount(result, output.length)) {
                session.copyOutputs();
            }
            return result;
        });
    }

    static int findNearestIndex(
        double originX,
        double originY,
        double originZ,
        double maxDistanceSquared,
        double[] positions
    ) {
        return withIndexSession(session -> session.invoke(
            Function.FIND_NEAREST_INDEX_DOUBLE,
            originX,
            originY,
            originZ,
            maxDistanceSquared,
            session.input(positions, Kind.DOUBLE)
        ));
    }

    static int findNearestIndexExclusive(
        double originX,
        double originY,
        double originZ,
        double maxDistanceSquared,
        double[] positions
    ) {
        return withIndexSession(session -> session.invoke(
            Function.FIND_NEAREST_INDEX_EXCLUSIVE_DOUBLE,
            originX,
            originY,
            originZ,
            maxDistanceSquared,
            session.input(positions, Kind.DOUBLE)
        ));
    }

    static int hasAnyWithinRadiusExclusive(
        double originX,
        double originY,
        double originZ,
        double maxDistanceSquared,
        double[] positions
    ) {
        return withIndexSession(session -> session.invoke(
            Function.HAS_ANY_WITHIN_RADIUS_EXCLUSIVE_DOUBLE,
            originX,
            originY,
            originZ,
            maxDistanceSquared,
            session.input(positions, Kind.DOUBLE)
        ));
    }

    static int findNearestBlockCenterIndex(
        double originX,
        double originY,
        double originZ,
        int[] positions
    ) {
        return withIndexSession(session -> session.invoke(
            Function.FIND_NEAREST_BLOCK_CENTER_INDEX,
            originX,
            originY,
            originZ,
            session.input(positions, Kind.INT)
        ));
    }

    static int findNearestBlockCenterIndex(
        double originX,
        double originY,
        double originZ,
        int[] positions,
        int positionCount
    ) {
        return withIndexSession(session -> session.invoke(
            Function.FIND_NEAREST_BLOCK_CENTER_INDEX_PREFIX,
            originX,
            originY,
            originZ,
            session.input(positions, Kind.INT),
            positionCount
        ));
    }

    static int findNearestBlockCornerIndex(int originX, int originY, int originZ, int[] positions) {
        return withIndexSession(session -> session.invoke(
            Function.FIND_NEAREST_BLOCK_CORNER_INDEX,
            originX,
            originY,
            originZ,
            session.input(positions, Kind.INT)
        ));
    }

    static int findNearestPackedBlockCornerIndex(int originX, int originY, int originZ, long[] packedPositions) {
        return withIndexSession(session -> session.invoke(
            Function.FIND_NEAREST_PACKED_BLOCK_CORNER_INDEX,
            originX,
            originY,
            originZ,
            session.input(packedPositions, Kind.LONG)
        ));
    }

    static int findNearestBlockCornerIndexWithinRadius(
        int originX,
        int originY,
        int originZ,
        long radiusSquared,
        int[] positions
    ) {
        return withIndexSession(session -> session.invoke(
            Function.FIND_NEAREST_BLOCK_CORNER_INDEX_WITHIN_RADIUS,
            originX,
            originY,
            originZ,
            radiusSquared,
            session.input(positions, Kind.INT)
        ));
    }

    static int findNearestPackedBlockCornerIndexWithinRadius(
        int originX,
        int originY,
        int originZ,
        long radiusSquared,
        long[] packedPositions
    ) {
        return withIndexSession(session -> session.invoke(
            Function.FIND_NEAREST_PACKED_BLOCK_CORNER_INDEX_WITHIN_RADIUS,
            originX,
            originY,
            originZ,
            radiusSquared,
            session.input(packedPositions, Kind.LONG)
        ));
    }

    static int filterWithinAabb(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        double[] positions,
        int[] output
    ) {
        return withSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.DOUBLE);
            Buffer outputBuffer = session.output(output, Kind.INT);
            int result = session.invoke(
                Function.FILTER_WITHIN_AABB_DOUBLE,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                positionsBuffer,
                outputBuffer
            );
            if (isValidCount(result, output.length)) {
                session.copyOutputs();
            }
            return result;
        });
    }

    static int filterIntersectingAabb(
        double queryMinX,
        double queryMinY,
        double queryMinZ,
        double queryMaxX,
        double queryMaxY,
        double queryMaxZ,
        double[] boxes,
        int[] output
    ) {
        return withSession(session -> {
            Buffer boxesBuffer = session.input(boxes, Kind.DOUBLE);
            Buffer outputBuffer = session.output(output, Kind.INT);
            int result = session.invoke(
                Function.FILTER_INTERSECTING_AABB_DOUBLE,
                queryMinX,
                queryMinY,
                queryMinZ,
                queryMaxX,
                queryMaxY,
                queryMaxZ,
                boxesBuffer,
                outputBuffer
            );
            if (isValidCount(result, output.length)) {
                session.copyOutputs();
            }
            return result;
        });
    }

    static int sortByDistance(int originX, int originY, int originZ, int[] positions, int[] output) {
        return withStatusSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.INT);
            Buffer outputBuffer = session.output(output, Kind.INT);
            int result = session.invoke(
                Function.SORT_BY_DISTANCE,
                originX,
                originY,
                originZ,
                positionsBuffer,
                outputBuffer
            );
            if (result == NativeStatus.OK.code()) {
                session.copyOutputs();
            }
            return result;
        });
    }

    static int sortByBlockDistance(int originX, int originY, int originZ, int[] positions, int[] output) {
        return withStatusSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.INT);
            Buffer outputBuffer = session.output(output, Kind.INT);
            int result = session.invoke(
                Function.SORT_BY_BLOCK_DISTANCE,
                originX,
                originY,
                originZ,
                positionsBuffer,
                outputBuffer
            );
            if (result == NativeStatus.OK.code()) {
                session.copyOutputs();
            }
            return result;
        });
    }

    static int sortByDistance(double originX, double originY, double originZ, double[] positions, int[] output) {
        return withStatusSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.DOUBLE);
            Buffer outputBuffer = session.uninitializedOutput(output, Kind.INT);
            int result = session.invokeSortByDistance(
                originX,
                originY,
                originZ,
                positionsBuffer,
                outputBuffer
            );
            if (result == NativeStatus.OK.code()) {
                session.copyOutput(outputBuffer, outputBuffer.length);
            }
            return result;
        });
    }

    static int sortByDistanceAndCountWithinRadiusExclusive(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    ) {
        return sortByDistanceAndCountWithinRadiusExclusive(
            originX,
            originY,
            originZ,
            radiusSquared,
            positions,
            positions.length,
            output,
            output.length
        );
    }

    static int sortByDistanceAndCountWithinRadiusExclusive(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int positionsLength,
        int[] output,
        int outputLength
    ) {
        return withSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.DOUBLE, positionsLength);
            Buffer outputBuffer = session.uninitializedOutput(output, Kind.INT, outputLength);
            int result = session.invokeSortByDistanceAndCountWithinRadiusExclusive(
                originX,
                originY,
                originZ,
                radiusSquared,
                positionsBuffer,
                outputBuffer
            );
            if (isValidCount(result, outputBuffer.length)) {
                session.copyOutput(outputBuffer, outputBuffer.length);
            }
            return result;
        });
    }

    static int selectNearestIndicesWithinRadiusExclusive(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int limit,
        int[] output
    ) {
        return selectNearestIndicesWithinRadiusExclusive(
            originX,
            originY,
            originZ,
            radiusSquared,
            positions,
            positions.length,
            limit,
            output,
            output.length
        );
    }

    static int selectNearestIndicesWithinRadiusExclusive(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int positionsLength,
        int limit,
        int[] output,
        int outputLength
    ) {
        return withSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.DOUBLE, positionsLength);
            Buffer outputBuffer = session.uninitializedOutput(output, Kind.INT, outputLength);
            int result = session.invokeSelectNearestIndicesWithinRadiusExclusive(
                originX,
                originY,
                originZ,
                radiusSquared,
                positionsBuffer,
                limit,
                outputBuffer
            );
            if (isValidCount(result, outputBuffer.length)) {
                session.copyOutput(outputBuffer, result);
            }
            return result;
        });
    }

    static int sortWithinRadiusExclusive(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    ) {
        return withSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.DOUBLE);
            Buffer outputBuffer = session.output(output, Kind.INT);
            int result = session.invoke(
                Function.SORT_WITHIN_RADIUS_EXCLUSIVE_DOUBLE,
                originX,
                originY,
                originZ,
                radiusSquared,
                positionsBuffer,
                outputBuffer
            );
            if (isValidCount(result, output.length)) {
                session.copyOutputs();
            }
            return result;
        });
    }

    static int sortWithinRadiusExclusivePrefixOutput(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    ) {
        return withSession(session -> {
            Buffer positionsBuffer = session.input(positions, Kind.DOUBLE);
            Buffer outputBuffer = session.uninitializedOutput(output, Kind.INT);
            int result = session.invokeSortWithinRadiusExclusive(
                originX,
                originY,
                originZ,
                radiusSquared,
                positionsBuffer,
                outputBuffer
            );
            if (isValidCount(result, output.length)) {
                session.copyOutput(outputBuffer, result);
            }
            return result;
        });
    }

    private static boolean isValidCount(int result, int capacity) {
        return result >= 0 && result <= capacity;
    }

    private static int withSession(SessionCall call) {
        return withSession(call, -1 - FFM_ERROR);
    }

    private static int withStatusSession(SessionCall call) {
        return withSession(call, FFM_ERROR);
    }

    private static int withIndexSession(SessionCall call) {
        return withSession(call, -1 - FFM_ERROR);
    }

    private static int withSession(SessionCall call, int failureResult) {
        Runtime current = runtime;
        if (current == null) {
            return failureResult;
        }

        try {
            Session session = current.session();
            session.reset();
            return call.run(session);
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError error) {
                throw error;
            }
            return failureResult;
        }
    }

    @FunctionalInterface
    private interface SessionCall {
        int run(Session session) throws Throwable;
    }

    private enum Kind {
        ADDRESS(0, 1),
        INT(4, 4),
        LONG(8, 8),
        DOUBLE(8, 8);

        private final int byteSize;
        private final long alignment;

        Kind(int byteSize, long alignment) {
            this.byteSize = byteSize;
            this.alignment = alignment;
        }

        private Class<?> carrier() {
            return switch (this) {
                case ADDRESS -> Object.class;
                case INT -> int.class;
                case LONG -> long.class;
                case DOUBLE -> double.class;
            };
        }
    }

    private enum Function {
        COMPUTE_SQUARED_DISTANCES("beryllium_compute_squared_distances", Kind.INT, Kind.INT, Kind.INT, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        SELECT_NEAREST_CHUNK_INDICES("beryllium_select_nearest_chunk_indices", Kind.INT, Kind.INT, Kind.ADDRESS, Kind.LONG, Kind.INT, Kind.ADDRESS, Kind.LONG),
        COMPUTE_SQUARED_DISTANCES_DOUBLE("beryllium_compute_squared_distances_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        POTENTIAL_SET_CHARGES("beryllium_potential_set_charges", Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        POTENTIAL_COMPUTE_CACHED("beryllium_potential_compute_cached", Kind.INT, Kind.INT, Kind.INT, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG),
        POTENTIAL_CUBECL_STATUS("beryllium_potential_cubecl_status"),
        COMPUTE_POTENTIAL_ENERGY_CHANGE("beryllium_compute_potential_energy_change", Kind.INT, Kind.INT, Kind.INT, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG),
        FILTER_WITHIN_RADIUS("beryllium_filter_within_radius", Kind.INT, Kind.INT, Kind.INT, Kind.LONG, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        COUNT_WITHIN_RADIUS("beryllium_count_within_radius", Kind.INT, Kind.INT, Kind.INT, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        FILTER_WITHIN_RADIUS_DOUBLE("beryllium_filter_within_radius_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        FILTER_WITHIN_RADIUS_EXCLUSIVE_DOUBLE("beryllium_filter_within_radius_exclusive_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        FILTER_WITHIN_EXCLUSIVE_CHUNK_DISTANCE("beryllium_filter_within_exclusive_chunk_distance", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        FILTER_WITHIN_RADII_DOUBLE("beryllium_filter_within_radii_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        FIND_NEAREST_INDEX_DOUBLE("beryllium_find_nearest_index_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG),
        FIND_NEAREST_INDEX_EXCLUSIVE_DOUBLE("beryllium_find_nearest_index_exclusive_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG),
        HAS_ANY_WITHIN_RADIUS_EXCLUSIVE_DOUBLE("beryllium_has_any_within_radius_exclusive_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG),
        FIND_NEAREST_BLOCK_CENTER_INDEX("beryllium_find_nearest_block_center_index", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG),
        FIND_NEAREST_BLOCK_CENTER_INDEX_PREFIX("beryllium_find_nearest_block_center_index_prefix", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.INT),
        FIND_NEAREST_BLOCK_CORNER_INDEX("beryllium_find_nearest_block_corner_index", Kind.INT, Kind.INT, Kind.INT, Kind.ADDRESS, Kind.LONG),
        FIND_NEAREST_BLOCK_CORNER_INDEX_WITHIN_RADIUS("beryllium_find_nearest_block_corner_index_within_radius", Kind.INT, Kind.INT, Kind.INT, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        FIND_NEAREST_PACKED_BLOCK_CORNER_INDEX("beryllium_find_nearest_packed_block_corner_index", Kind.INT, Kind.INT, Kind.INT, Kind.ADDRESS, Kind.LONG),
        FIND_NEAREST_PACKED_BLOCK_CORNER_INDEX_WITHIN_RADIUS("beryllium_find_nearest_packed_block_corner_index_within_radius", Kind.INT, Kind.INT, Kind.INT, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        FILTER_WITHIN_AABB_DOUBLE("beryllium_filter_within_aabb_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        FILTER_INTERSECTING_AABB_DOUBLE("beryllium_filter_intersecting_aabb_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        SORT_BY_DISTANCE("beryllium_sort_by_distance", Kind.INT, Kind.INT, Kind.INT, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        SORT_BY_BLOCK_DISTANCE("beryllium_sort_by_block_distance", Kind.INT, Kind.INT, Kind.INT, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        SORT_BY_DISTANCE_DOUBLE("beryllium_sort_by_distance_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        SORT_BY_DISTANCE_AND_COUNT_WITHIN_RADIUS_EXCLUSIVE_DOUBLE("beryllium_sort_by_distance_and_count_within_radius_exclusive_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        SELECT_NEAREST_INDICES_WITHIN_RADIUS_EXCLUSIVE_DOUBLE("beryllium_select_nearest_indices_within_radius_exclusive_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.INT, Kind.ADDRESS, Kind.LONG),
        SORT_WITHIN_RADIUS_EXCLUSIVE_DOUBLE("beryllium_sort_within_radius_exclusive_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG);

        private final String symbol;
        private final Kind[] arguments;

        Function(String symbol, Kind... arguments) {
            this.symbol = symbol;
            this.arguments = arguments;
        }

        private boolean usesExactHandle() {
            return switch (this) {
                case SELECT_NEAREST_CHUNK_INDICES,
                    POTENTIAL_SET_CHARGES,
                    POTENTIAL_COMPUTE_CACHED,
                    COMPUTE_POTENTIAL_ENERGY_CHANGE,
                    SORT_BY_DISTANCE_DOUBLE,
                    SORT_BY_DISTANCE_AND_COUNT_WITHIN_RADIUS_EXCLUSIVE_DOUBLE,
                    SELECT_NEAREST_INDICES_WITHIN_RADIUS_EXCLUSIVE_DOUBLE,
                    SORT_WITHIN_RADIUS_EXCLUSIVE_DOUBLE -> true;
                default -> false;
            };
        }
    }

    private static final class Runtime {
        private final Method arenaOfShared;
        private final Method arenaAllocate;
        private final MethodHandle copyArrayToSegment;
        private final MethodHandle copySegmentToArray;
        private final ThreadLocal<Session> sessions;
        private final EnumMap<Kind, Object> layouts = new EnumMap<>(Kind.class);
        private final EnumMap<Function, MethodHandle> handles = new EnumMap<>(Function.class);
        private final MethodHandle[] exactHandles = new MethodHandle[Function.values().length];

        private Runtime() throws ReflectiveOperationException {
            Class<?> arenaClass = Class.forName("java.lang.foreign.Arena");
            Class<?> memoryLayoutClass = Class.forName("java.lang.foreign.MemoryLayout");
            Class<?> memorySegmentClass = Class.forName("java.lang.foreign.MemorySegment");
            Class<?> valueLayoutClass = Class.forName("java.lang.foreign.ValueLayout");
            Class<?> functionDescriptorClass = Class.forName("java.lang.foreign.FunctionDescriptor");
            Class<?> linkerClass = Class.forName("java.lang.foreign.Linker");
            Class<?> linkerOptionClass = Class.forName("java.lang.foreign.Linker$Option");
            Class<?> symbolLookupClass = Class.forName("java.lang.foreign.SymbolLookup");

            arenaOfShared = arenaClass.getMethod("ofShared");
            arenaAllocate = arenaClass.getMethod("allocate", long.class, long.class);
            Method copyArrayToSegmentMethod = memorySegmentClass.getMethod(
                "copy",
                Object.class,
                int.class,
                memorySegmentClass,
                valueLayoutClass,
                long.class,
                int.class
            );
            copyArrayToSegment = MethodHandles.publicLookup().unreflect(copyArrayToSegmentMethod).asType(
                MethodType.methodType(
                    void.class,
                    Object.class,
                    int.class,
                    Object.class,
                    Object.class,
                    long.class,
                    int.class
                )
            );
            Method copySegmentToArrayMethod = memorySegmentClass.getMethod(
                "copy",
                memorySegmentClass,
                valueLayoutClass,
                long.class,
                Object.class,
                int.class,
                int.class
            );
            copySegmentToArray = MethodHandles.publicLookup().unreflect(copySegmentToArrayMethod).asType(
                MethodType.methodType(
                    void.class,
                    Object.class,
                    Object.class,
                    long.class,
                    Object.class,
                    int.class,
                    int.class
                )
            );

            layouts.put(Kind.ADDRESS, valueLayoutClass.getField("ADDRESS").get(null));
            layouts.put(Kind.INT, valueLayoutClass.getField("JAVA_INT").get(null));
            layouts.put(Kind.LONG, valueLayoutClass.getField("JAVA_LONG").get(null));
            layouts.put(Kind.DOUBLE, valueLayoutClass.getField("JAVA_DOUBLE").get(null));

            Object linker = linkerClass.getMethod("nativeLinker").invoke(null);
            Object lookup = symbolLookupClass.getMethod("loaderLookup").invoke(null);
            Method descriptorOf = functionDescriptorClass.getMethod(
                "of",
                memoryLayoutClass,
                Array.newInstance(memoryLayoutClass, 0).getClass()
            );
            Method find = symbolLookupClass.getMethod("find", String.class);
            Method downcallHandle = linkerClass.getMethod(
                "downcallHandle",
                memorySegmentClass,
                functionDescriptorClass,
                Array.newInstance(linkerOptionClass, 0).getClass()
            );

            for (Function function : Function.values()) {
                Object argumentLayouts = Array.newInstance(memoryLayoutClass, function.arguments.length);
                for (int index = 0; index < function.arguments.length; index++) {
                    Array.set(argumentLayouts, index, layouts.get(function.arguments[index]));
                }

                Object descriptor = descriptorOf.invoke(null, layouts.get(Kind.INT), argumentLayouts);
                Optional<?> address = (Optional<?>) find.invoke(lookup, function.symbol);
                Object addressSegment = address.orElseThrow(() -> new IllegalStateException("Missing FFM symbol " + function.symbol));
                Object options = Array.newInstance(linkerOptionClass, 0);
                MethodHandle handle = (MethodHandle) downcallHandle.invoke(
                    linker,
                    addressSegment,
                    descriptor,
                    options
                );
                handles.put(function, handle);
                if (function.usesExactHandle()) {
                    Class<?>[] parameterTypes = new Class<?>[function.arguments.length];
                    for (int index = 0; index < function.arguments.length; index++) {
                        parameterTypes[index] = function.arguments[index].carrier();
                    }
                    exactHandles[function.ordinal()] = handle.asType(
                        MethodType.methodType(int.class, parameterTypes)
                    );
                }
            }

            sessions = ThreadLocal.withInitial(() -> new Session(this));
        }

        private Session session() {
            return sessions.get();
        }

        private long sessionIdForCurrentThread() {
            return session().id();
        }

        private AutoCloseable newArena() throws ReflectiveOperationException {
            return (AutoCloseable) arenaOfShared.invoke(null);
        }

        private Object allocate(AutoCloseable arena, Kind kind, int length) throws ReflectiveOperationException {
            long bytes = Math.multiplyExact((long) length, kind.byteSize);
            return arenaAllocate.invoke(arena, bytes, kind.alignment);
        }

        private Object layout(Kind kind) {
            return layouts.get(kind);
        }

        private MethodHandle handle(Function function) {
            return handles.get(function);
        }

        private MethodHandle exactHandle(Function function) {
            return exactHandles[function.ordinal()];
        }
    }

    private static final class Session {
        private final Runtime runtime;
        private final List<Buffer> buffers = new ArrayList<>();
        private final List<Buffer> outputs = new ArrayList<>();
        private final long id = NEXT_SESSION_ID.incrementAndGet();
        private int nextBufferIndex;

        private Session(Runtime runtime) {
            this.runtime = runtime;
        }

        private void reset() {
            nextBufferIndex = 0;
            outputs.clear();
        }

        private Buffer input(Object array, Kind kind) throws Throwable {
            return input(array, kind, java.lang.reflect.Array.getLength(array));
        }

        private Buffer input(Object array, Kind kind, int length) throws Throwable {
            Buffer buffer = nextBuffer(array, kind, length);
            copyToNative(array, buffer.segment, kind, length);
            return buffer;
        }

        private Buffer output(Object array, Kind kind) throws Throwable {
            return output(array, kind, java.lang.reflect.Array.getLength(array));
        }

        private Buffer output(Object array, Kind kind, int length) throws Throwable {
            Buffer buffer = nextBuffer(array, kind, length);
            copyToNative(array, buffer.segment, kind, length);
            outputs.add(buffer);
            return buffer;
        }

        private Buffer uninitializedOutput(Object array, Kind kind) throws Throwable {
            return uninitializedOutput(array, kind, java.lang.reflect.Array.getLength(array));
        }

        private Buffer uninitializedOutput(Object array, Kind kind, int length) throws Throwable {
            return nextBuffer(array, kind, length);
        }

        private Buffer nextBuffer(Object array, Kind kind, int length) throws Throwable {
            int arrayLength = java.lang.reflect.Array.getLength(array);
            if (length < 0 || length > arrayLength) {
                throw new IllegalArgumentException("buffer length must be within the array bounds");
            }
            int index = nextBufferIndex++;
            Buffer buffer;
            if (index == buffers.size()) {
                buffer = new Buffer(runtime, kind, length);
                buffers.add(buffer);
            } else {
                buffer = buffers.get(index);
                buffer.ensureCapacity(runtime, kind, length);
            }
            buffer.array = array;
            buffer.length = length;
            return buffer;
        }

        private int invoke(Function function, Object... arguments) throws Throwable {
            List<Object> expanded = new ArrayList<>();
            for (Object argument : arguments) {
                if (argument instanceof Buffer buffer) {
                    expanded.add(buffer.segment);
                    expanded.add((long) buffer.length);
                } else {
                    expanded.add(argument);
                }
            }
            Object result = runtime.handle(function).invokeWithArguments(expanded);
            return ((Number) result).intValue();
        }

        private int invokeSelectNearestChunkIndices(
            int originX,
            int originZ,
            Buffer positions,
            int limit,
            Buffer output
        ) throws Throwable {
            return (int) runtime.exactHandle(Function.SELECT_NEAREST_CHUNK_INDICES).invokeExact(
                originX,
                originZ,
                positions.segment,
                (long) positions.length,
                limit,
                output.segment,
                (long) output.length
            );
        }

        private int invokeComputePotentialEnergyChange(
            int originX,
            int originY,
            int originZ,
            Buffer positions,
            Buffer charges,
            double chargeMultiplier,
            Buffer output
        ) throws Throwable {
            return (int) runtime.exactHandle(Function.COMPUTE_POTENTIAL_ENERGY_CHANGE).invokeExact(
                originX,
                originY,
                originZ,
                positions.segment,
                (long) positions.length,
                charges.segment,
                (long) charges.length,
                chargeMultiplier,
                output.segment,
                (long) output.length
            );
        }

        private int invokeSetPotentialCharges(Buffer positions, Buffer charges) throws Throwable {
            return (int) runtime.exactHandle(Function.POTENTIAL_SET_CHARGES).invokeExact(
                positions.segment,
                (long) positions.length,
                charges.segment,
                (long) charges.length
            );
        }

        private int invokeComputePotentialEnergyChangeCached(
            int originX,
            int originY,
            int originZ,
            double chargeMultiplier,
            Buffer output
        ) throws Throwable {
            return (int) runtime.exactHandle(Function.POTENTIAL_COMPUTE_CACHED).invokeExact(
                originX,
                originY,
                originZ,
                chargeMultiplier,
                output.segment,
                (long) output.length
            );
        }

        private int invokeSortByDistance(
            double originX,
            double originY,
            double originZ,
            Buffer positions,
            Buffer output
        ) throws Throwable {
            return (int) runtime.exactHandle(Function.SORT_BY_DISTANCE_DOUBLE).invokeExact(
                originX,
                originY,
                originZ,
                positions.segment,
                (long) positions.length,
                output.segment,
                (long) output.length
            );
        }

        private int invokeSortByDistanceAndCountWithinRadiusExclusive(
            double originX,
            double originY,
            double originZ,
            double radiusSquared,
            Buffer positions,
            Buffer output
        ) throws Throwable {
            return (int) runtime.exactHandle(
                Function.SORT_BY_DISTANCE_AND_COUNT_WITHIN_RADIUS_EXCLUSIVE_DOUBLE
            ).invokeExact(
                originX,
                originY,
                originZ,
                radiusSquared,
                positions.segment,
                (long) positions.length,
                output.segment,
                (long) output.length
            );
        }

        private int invokeSelectNearestIndicesWithinRadiusExclusive(
            double originX,
            double originY,
            double originZ,
            double radiusSquared,
            Buffer positions,
            int limit,
            Buffer output
        ) throws Throwable {
            return (int) runtime.exactHandle(
                Function.SELECT_NEAREST_INDICES_WITHIN_RADIUS_EXCLUSIVE_DOUBLE
            ).invokeExact(
                originX,
                originY,
                originZ,
                radiusSquared,
                positions.segment,
                (long) positions.length,
                limit,
                output.segment,
                (long) output.length
            );
        }

        private int invokeSortWithinRadiusExclusive(
            double originX,
            double originY,
            double originZ,
            double radiusSquared,
            Buffer positions,
            Buffer output
        ) throws Throwable {
            return (int) runtime.exactHandle(Function.SORT_WITHIN_RADIUS_EXCLUSIVE_DOUBLE).invokeExact(
                originX,
                originY,
                originZ,
                radiusSquared,
                positions.segment,
                (long) positions.length,
                output.segment,
                (long) output.length
            );
        }

        private void copyOutputs() throws Throwable {
            for (Buffer output : outputs) {
                copyOutput(output, output.length);
            }
        }

        private void copyOutput(Buffer output, int length) throws Throwable {
            if (length < 0 || length > output.length) {
                throw new IllegalArgumentException("output copy length must be within the native buffer bounds");
            }
            if (length > 0) {
                runtime.copySegmentToArray.invokeExact(
                    output.segment,
                    runtime.layout(output.kind),
                    0L,
                    output.array,
                    0,
                    length
                );
            }
        }

        private void copyToNative(Object array, Object segment, Kind kind, int length) throws Throwable {
            if (length > 0) {
                runtime.copyArrayToSegment.invokeExact(
                    array,
                    0,
                    segment,
                    runtime.layout(kind),
                    0L,
                    length
                );
            }
        }

        private long id() {
            return id;
        }
    }

    private static final class Buffer {
        private AutoCloseable arena;
        private Object array;
        private Object segment;
        private Kind kind;
        private int capacity;
        private int length;

        private Buffer(Runtime runtime, Kind kind, int capacity) throws Throwable {
            arena = runtime.newArena();
            try {
                segment = runtime.allocate(arena, kind, capacity);
            } catch (Throwable failure) {
                closeQuietly(arena, failure);
                throw failure;
            }
            this.kind = kind;
            this.capacity = capacity;
        }

        private void ensureCapacity(Runtime runtime, Kind requestedKind, int requestedLength) throws Throwable {
            if (kind != requestedKind || capacity < requestedLength) {
                AutoCloseable replacement = runtime.newArena();
                Object replacementSegment;
                try {
                    replacementSegment = runtime.allocate(replacement, requestedKind, requestedLength);
                } catch (Throwable failure) {
                    closeQuietly(replacement, failure);
                    throw failure;
                }

                AutoCloseable previous = arena;
                arena = replacement;
                segment = replacementSegment;
                kind = requestedKind;
                capacity = requestedLength;
                if (previous != null) {
                    previous.close();
                }
            }
        }

        private static void closeQuietly(AutoCloseable resource, Throwable failure) {
            try {
                resource.close();
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }
}
