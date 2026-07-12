package alku.beryllium.bridge;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

/**
 * Java 21 Foreign Function & Memory bridge.
 *
 * The FFM API is a preview API on the Java version supported by Minecraft
 * 1.21.1. Reflection keeps this common module compilable without preview
 * types while still using native downcalls at runtime.
 */
final class FfmNativeBridge {
    private static final int FFM_ERROR = NativeStatus.FFM_ERROR.code();
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
            Buffer outputBuffer = session.output(output, Kind.DOUBLE);
            int result = session.invoke(
                Function.COMPUTE_POTENTIAL_ENERGY_CHANGE,
                originX,
                originY,
                originZ,
                positionsBuffer,
                chargesBuffer,
                chargeMultiplier,
                outputBuffer
            );
            if (result == NativeStatus.OK.code()) {
                session.copyOutputs();
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
            Buffer outputBuffer = session.output(output, Kind.INT);
            int result = session.invoke(
                Function.SORT_BY_DISTANCE_DOUBLE,
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

    static int sortByDistanceAndCountWithinRadiusExclusive(
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
                Function.SORT_BY_DISTANCE_AND_COUNT_WITHIN_RADIUS_EXCLUSIVE_DOUBLE,
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

        try (Session session = current.openSession()) {
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
    }

    private enum Function {
        COMPUTE_SQUARED_DISTANCES("beryllium_compute_squared_distances", Kind.INT, Kind.INT, Kind.INT, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        COMPUTE_SQUARED_DISTANCES_DOUBLE("beryllium_compute_squared_distances_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
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
        FILTER_WITHIN_AABB_DOUBLE("beryllium_filter_within_aabb_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        FILTER_INTERSECTING_AABB_DOUBLE("beryllium_filter_intersecting_aabb_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        SORT_BY_DISTANCE("beryllium_sort_by_distance", Kind.INT, Kind.INT, Kind.INT, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        SORT_BY_BLOCK_DISTANCE("beryllium_sort_by_block_distance", Kind.INT, Kind.INT, Kind.INT, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        SORT_BY_DISTANCE_DOUBLE("beryllium_sort_by_distance_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        SORT_BY_DISTANCE_AND_COUNT_WITHIN_RADIUS_EXCLUSIVE_DOUBLE("beryllium_sort_by_distance_and_count_within_radius_exclusive_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG),
        SORT_WITHIN_RADIUS_EXCLUSIVE_DOUBLE("beryllium_sort_within_radius_exclusive_double", Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.DOUBLE, Kind.ADDRESS, Kind.LONG, Kind.ADDRESS, Kind.LONG);

        private final String symbol;
        private final Kind[] arguments;

        Function(String symbol, Kind... arguments) {
            this.symbol = symbol;
            this.arguments = arguments;
        }
    }

    private static final class Runtime {
        private final Method arenaOfShared;
        private final Method arenaAllocate;
        private final Method copyArrayToSegment;
        private final Method copySegmentToArray;
        private final EnumMap<Kind, Object> layouts = new EnumMap<>(Kind.class);
        private final EnumMap<Function, MethodHandle> handles = new EnumMap<>(Function.class);

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
            copyArrayToSegment = memorySegmentClass.getMethod(
                "copy",
                Object.class,
                int.class,
                memorySegmentClass,
                valueLayoutClass,
                long.class,
                int.class
            );
            copySegmentToArray = memorySegmentClass.getMethod(
                "copy",
                memorySegmentClass,
                valueLayoutClass,
                long.class,
                Object.class,
                int.class,
                int.class
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
            }
        }

        private Session openSession() throws ReflectiveOperationException {
            return new Session(this, (AutoCloseable) arenaOfShared.invoke(null));
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
    }

    private static final class Session implements AutoCloseable {
        private final Runtime runtime;
        private final AutoCloseable arena;
        private final List<Buffer> outputs = new ArrayList<>();

        private Session(Runtime runtime, AutoCloseable arena) {
            this.runtime = runtime;
            this.arena = arena;
        }

        private Buffer input(Object array, Kind kind) throws ReflectiveOperationException {
            Object segment = runtime.allocate(arena, kind, java.lang.reflect.Array.getLength(array));
            copyToNative(array, segment, kind);
            return new Buffer(array, segment, kind);
        }

        private Buffer output(Object array, Kind kind) throws ReflectiveOperationException {
            Object segment = runtime.allocate(arena, kind, java.lang.reflect.Array.getLength(array));
            copyToNative(array, segment, kind);
            Buffer buffer = new Buffer(array, segment, kind);
            outputs.add(buffer);
            return buffer;
        }

        private int invoke(Function function, Object... arguments) throws Throwable {
            List<Object> expanded = new ArrayList<>();
            for (Object argument : arguments) {
                if (argument instanceof Buffer buffer) {
                    expanded.add(buffer.segment);
                    expanded.add((long) java.lang.reflect.Array.getLength(buffer.array));
                } else {
                    expanded.add(argument);
                }
            }
            Object result = runtime.handle(function).invokeWithArguments(expanded);
            return ((Number) result).intValue();
        }

        private void copyOutputs() throws ReflectiveOperationException {
            for (Buffer output : outputs) {
                int length = java.lang.reflect.Array.getLength(output.array);
                if (length > 0) {
                    runtime.copySegmentToArray.invoke(
                        null,
                        output.segment,
                        runtime.layout(output.kind),
                        0L,
                        output.array,
                        0,
                        length
                    );
                }
            }
        }

        private void copyToNative(Object array, Object segment, Kind kind) throws ReflectiveOperationException {
            int length = java.lang.reflect.Array.getLength(array);
            if (length > 0) {
                runtime.copyArrayToSegment.invoke(
                    null,
                    array,
                    0,
                    segment,
                    runtime.layout(kind),
                    0L,
                    length
                );
            }
        }

        @Override
        public void close() throws Exception {
            arena.close();
        }
    }

    private static final class Buffer {
        private final Object array;
        private final Object segment;
        private final Kind kind;

        private Buffer(Object array, Object segment, Kind kind) {
            this.array = array;
            this.segment = segment;
            this.kind = kind;
        }
    }
}
