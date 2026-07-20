package alku.beryllium.verify;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.bridge.NativeStatus;
import alku.beryllium.compute.JavaComputeKernels;
import alku.beryllium.compute.EntityDistanceSort;
import alku.beryllium.compute.EntityPacking;
import alku.beryllium.compute.ChunkDistanceSearch;
import com.google.common.collect.Comparators;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * Small repeatable benchmark for the nearest-item query's distance stage.
 * This is intentionally a JavaExec verifier instead of a JUnit test so the
 * bundled native library is loaded and the result is printed for inspection.
 */
public final class BerylliumPerformanceBenchmark {
    private static final int[] CANDIDATE_COUNTS = {256, 1024, 4096, 8192, 16384};
    private static final int[] CHUNK_PLAYER_COUNTS = {32, 128, 512, 2048, 4096, 8192};
    private static final int[] CHUNK_SEND_CANDIDATE_COUNTS = {128, 512, 2048, 4096, 8192};
    private static final int[] CHUNK_SEND_LIMITS = {9, 64};
    private static final int WARMUP_ITERATIONS = 100;
    private static final int MEASUREMENT_ITERATIONS = 300;
    private static final int POTENTIAL_CHARGE_COUNT = 8192;
    private static final double RADIUS = 32.0;
    private static long blackHole;

    private BerylliumPerformanceBenchmark() {
    }

    public static void main(String[] args) {
        NativeStatus status = NativeBridge.initialize();
        if (status != NativeStatus.OK) {
            throw new AssertionError("Expected bundled native backend to load, got " + status);
        }

        System.out.printf(
            Locale.ROOT,
            "benchmark=nearest-item-distance warmup=%d measurements=%d radius=%.1f native=%s%n",
            WARMUP_ITERATIONS,
            MEASUREMENT_ITERATIONS,
            RADIUS,
            status
        );

        for (int candidateCount : CANDIDATE_COUNTS) {
            List<BenchmarkPoint> points = createPoints(candidateCount);
            long vanillaMedian = measure("vanilla_java", () -> vanillaQuery(points));
            long legacyMedian = measure("legacy_native", () -> legacyNativeQuery(points));
            long fusedMedian = measure("fused_native", () -> fusedNativeQuery(points));
            long topKMedian = measure("top_k_native", () -> topKNativeQuery(points));

            System.out.printf(
                Locale.ROOT,
                "result=candidates:%d vanilla_java_median_ns:%d legacy_native_median_ns:%d fused_native_median_ns:%d "
                    + "top_k_native_median_ns:%d legacy_speedup:%.2fx fused_speedup:%.2fx top_k_speedup:%.2fx "
                    + "top_k_vs_fused:%.2fx%n",
                candidateCount,
                vanillaMedian,
                legacyMedian,
                fusedMedian,
                topKMedian,
                speedup(vanillaMedian, legacyMedian),
                speedup(vanillaMedian, fusedMedian),
                speedup(vanillaMedian, topKMedian),
                speedup(fusedMedian, topKMedian)
            );
        }

        benchmarkPotentialEnergy();
        benchmarkChunkDistance();
        benchmarkChunkSendSelection();
        benchmarkNativeFilters();

        if (blackHole == Long.MIN_VALUE) {
            throw new AssertionError("benchmark black hole was not consumed");
        }
    }

    private static long measure(String name, Supplier<Optional<BenchmarkPoint>> query) {
        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
            consume(query.get());
        }

        long[] samples = new long[MEASUREMENT_ITERATIONS];
        for (int iteration = 0; iteration < MEASUREMENT_ITERATIONS; iteration++) {
            long start = System.nanoTime();
            consume(query.get());
            samples[iteration] = System.nanoTime() - start;
        }

        java.util.Arrays.sort(samples);
        long median = samples[samples.length / 2];
        System.out.printf(Locale.ROOT, "sample=%s median_ns=%d%n", name, median);
        return median;
    }

    private static void benchmarkPotentialEnergy() {
        int[] positions = new int[POTENTIAL_CHARGE_COUNT * 3];
        double[] charges = new double[POTENTIAL_CHARGE_COUNT];
        for (int index = 0; index < POTENTIAL_CHARGE_COUNT; index++) {
            int offset = index * 3;
            positions[offset] = (index % 4096) - 2048;
            positions[offset + 1] = 65 + (index % 31);
            positions[offset + 2] = (index % 31) - 15;
            charges[index] = (index % 17 - 8) * 0.25;
        }

        double multiplier = 0.75;
        long vanillaMedian = measureScalar(
            "vanilla_potential_energy",
            () -> JavaComputeKernels.potentialEnergyChange(0, 64, 0, positions, charges, multiplier)
        );
        long nativeMedian = measureScalar(
            "native_potential_energy",
            () -> NativeBridge.computePotentialEnergyChange(0, 64, 0, positions, charges, multiplier)
        );
        System.out.printf(
            Locale.ROOT,
            "potential_result=charges:%d vanilla_java_median_ns:%d native_median_ns:%d speedup:%.2fx%n",
            POTENTIAL_CHARGE_COUNT,
            vanillaMedian,
            nativeMedian,
            speedup(vanillaMedian, nativeMedian)
        );
    }

    private static long measureScalar(String name, DoubleSupplier calculation) {
        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
            consume(calculation.getAsDouble());
        }

        long[] samples = new long[MEASUREMENT_ITERATIONS];
        for (int iteration = 0; iteration < MEASUREMENT_ITERATIONS; iteration++) {
            long start = System.nanoTime();
            consume(calculation.getAsDouble());
            samples[iteration] = System.nanoTime() - start;
        }

        java.util.Arrays.sort(samples);
        long median = samples[samples.length / 2];
        System.out.printf(Locale.ROOT, "sample=%s median_ns=%d%n", name, median);
        return median;
    }

    private static void benchmarkChunkDistance() {
        double radiusSquared = 128.0 * 128.0;
        System.out.printf(
            Locale.ROOT,
            "benchmark=chunk-spawn-horizontal-distance radius=%.1f%n",
            Math.sqrt(radiusSquared)
        );

        for (int playerCount : CHUNK_PLAYER_COUNTS) {
            List<ChunkBenchmarkPlayer> players = createChunkPlayers(playerCount);
            long vanillaMedian = measureChunk("vanilla_chunk_spawn", () -> vanillaChunkQuery(players, radiusSquared));
            long berylliumMedian = measureChunk("beryllium_chunk_spawn", () -> berylliumChunkQuery(players, radiusSquared));
            System.out.printf(
                Locale.ROOT,
                "chunk_result=players:%d vanilla_java_median_ns:%d beryllium_median_ns:%d speedup:%.2fx%n",
                playerCount,
                vanillaMedian,
                berylliumMedian,
                speedup(vanillaMedian, berylliumMedian)
            );
        }
    }

    private static void benchmarkChunkSendSelection() {
        System.out.println("benchmark=chunk-send-top-k");
        for (int candidateCount : CHUNK_SEND_CANDIDATE_COUNTS) {
            LongSet pendingChunks = new LongOpenHashSet(createChunkSendPositions(candidateCount));
            for (int limit : CHUNK_SEND_LIMITS) {
                long vanillaMedian = measureLong(
                    "vanilla_chunk_send",
                    () -> vanillaChunkSendSelection(pendingChunks, limit)
                );
                long javaMedian = measureLong(
                    "java_chunk_send",
                    () -> javaChunkSendSelection(pendingChunks, limit)
                );
                long nativeMedian = measureLong(
                    "native_chunk_send",
                    () -> nativeChunkSendSelection(pendingChunks, limit)
                );
                System.out.printf(
                    Locale.ROOT,
                    "chunk_send_result=candidates:%d limit:%d vanilla_java_median_ns:%d "
                        + "primitive_java_median_ns:%d native_ffm_median_ns:%d "
                        + "java_speedup:%.2fx native_speedup:%.2fx native_vs_java:%.2fx%n",
                    candidateCount,
                    limit,
                    vanillaMedian,
                    javaMedian,
                    nativeMedian,
                    speedup(vanillaMedian, javaMedian),
                    speedup(vanillaMedian, nativeMedian),
                    speedup(javaMedian, nativeMedian)
                );
            }
        }
    }

    private static void benchmarkNativeFilters() {
        System.out.println("benchmark=entity-native-filters");
        for (int candidateCount : CANDIDATE_COUNTS) {
            double[] positions = createFilterPositions(candidateCount);
            double[] radiiSquared = createVariableRadiiSquared(candidateCount);
            double[] boxes = createFilterBoxes(candidateCount);

            int[] javaVariableRadiusOutput = new int[candidateCount];
            int[] nativeVariableRadiusOutput = new int[candidateCount];
            int javaVariableRadiusCount = JavaComputeKernels.filterWithinRadii(
                0.0,
                0.0,
                0.0,
                positions,
                radiiSquared,
                javaVariableRadiusOutput
            );
            int nativeVariableRadiusCount = NativeBridge.filterWithinRadii(
                0.0,
                0.0,
                0.0,
                positions,
                radiiSquared,
                nativeVariableRadiusOutput
            );
            assertFilterParity(
                "variable-radius filter",
                javaVariableRadiusOutput,
                javaVariableRadiusCount,
                nativeVariableRadiusOutput,
                nativeVariableRadiusCount
            );
            long javaVariableRadiusMedian = measureLong(
                "java_variable_radius_filter",
                () -> consumeFilterResult(
                    javaVariableRadiusOutput,
                    JavaComputeKernels.filterWithinRadii(
                        0.0,
                        0.0,
                        0.0,
                        positions,
                        radiiSquared,
                        javaVariableRadiusOutput
                    )
                )
            );
            long nativeVariableRadiusMedian = measureLong(
                "native_variable_radius_filter",
                () -> consumeFilterResult(
                    nativeVariableRadiusOutput,
                    NativeBridge.filterWithinRadii(
                        0.0,
                        0.0,
                        0.0,
                        positions,
                        radiiSquared,
                        nativeVariableRadiusOutput
                    )
                )
            );

            int[] javaAabbOutput = new int[candidateCount];
            int[] nativeAabbOutput = new int[candidateCount];
            int javaAabbCount = JavaComputeKernels.filterIntersectingAabb(
                -96.0,
                -32.0,
                -96.0,
                96.0,
                96.0,
                96.0,
                boxes,
                javaAabbOutput
            );
            int nativeAabbCount = NativeBridge.filterIntersectingAabb(
                -96.0,
                -32.0,
                -96.0,
                96.0,
                96.0,
                96.0,
                boxes,
                nativeAabbOutput
            );
            assertFilterParity(
                "AABB intersection filter",
                javaAabbOutput,
                javaAabbCount,
                nativeAabbOutput,
                nativeAabbCount
            );
            long javaAabbMedian = measureLong(
                "java_aabb_intersection_filter",
                () -> consumeFilterResult(
                    javaAabbOutput,
                    JavaComputeKernels.filterIntersectingAabb(
                        -96.0,
                        -32.0,
                        -96.0,
                        96.0,
                        96.0,
                        96.0,
                        boxes,
                        javaAabbOutput
                    )
                )
            );
            long nativeAabbMedian = measureLong(
                "native_aabb_intersection_filter",
                () -> consumeFilterResult(
                    nativeAabbOutput,
                    NativeBridge.filterIntersectingAabb(
                        -96.0,
                        -32.0,
                        -96.0,
                        96.0,
                        96.0,
                        96.0,
                        boxes,
                        nativeAabbOutput
                    )
                )
            );

            System.out.printf(
                Locale.ROOT,
                "entity_filter_result=candidates:%d variable_radius_java_median_ns:%d variable_radius_native_median_ns:%d "
                    + "variable_radius_speedup:%.2fx aabb_java_median_ns:%d aabb_native_median_ns:%d aabb_speedup:%.2fx%n",
                candidateCount,
                javaVariableRadiusMedian,
                nativeVariableRadiusMedian,
                speedup(javaVariableRadiusMedian, nativeVariableRadiusMedian),
                javaAabbMedian,
                nativeAabbMedian,
                speedup(javaAabbMedian, nativeAabbMedian)
            );
        }
    }

    private static long vanillaChunkSendSelection(LongSet pendingChunks, int limit) {
        List<Long> selected = pendingChunks
            .stream()
            .collect(Comparators.least(
                limit,
                Comparator.comparingInt(position -> chunkDistanceSquared(0, 0, position))
            ));
        long result = selected.size();
        for (long position : selected) {
            result = result * 31 + position;
        }
        return result;
    }

    private static long javaChunkSendSelection(LongSet pendingChunks, int limit) {
        long[] positions = pendingChunks.longStream().toArray();
        int[] output = new int[Math.min(limit, positions.length)];
        int count = JavaComputeKernels.selectNearestChunkIndices(0, 0, positions, limit, output);
        return consumeChunkSelection(positions, output, count);
    }

    private static long nativeChunkSendSelection(LongSet pendingChunks, int limit) {
        long[] positions = pendingChunks.longStream().toArray();
        int[] output = new int[Math.min(limit, positions.length)];
        int count = NativeBridge.selectNearestChunkIndices(0, 0, positions, limit, output);
        return consumeChunkSelection(positions, output, count);
    }

    private static long consumeChunkSelection(long[] positions, int[] selectedIndices, int count) {
        long result = count;
        for (int outputIndex = 0; outputIndex < count; outputIndex++) {
            result = result * 31 + positions[selectedIndices[outputIndex]];
        }
        return result;
    }

    private static long consumeFilterResult(int[] indices, int count) {
        long result = count;
        if (count > 0) {
            result = result * 31 + indices[0];
            result = result * 31 + indices[count - 1];
        }
        return result;
    }

    private static void assertFilterParity(
        String name,
        int[] expected,
        int expectedCount,
        int[] actual,
        int actualCount
    ) {
        if (expectedCount != actualCount) {
            throw new AssertionError(name + " count mismatch: expected " + expectedCount + " but got " + actualCount);
        }
        for (int index = 0; index < expectedCount; index++) {
            if (expected[index] != actual[index]) {
                throw new AssertionError(
                    name + " index mismatch at " + index + ": expected " + expected[index] + " but got " + actual[index]
                );
            }
        }
    }

    private static long[] createChunkSendPositions(int count) {
        long[] positions = new long[count];
        for (int index = 0; index < count; index++) {
            int x = index * 1103515245 + 12345;
            int z = Integer.rotateLeft(index * 0x9E3779B9, index & 31);
            positions[index] = (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
        }
        return positions;
    }

    private static int chunkDistanceSquared(int originX, int originZ, long packedPosition) {
        int dx = (int) packedPosition - originX;
        int dz = (int) (packedPosition >>> 32) - originZ;
        return dx * dx + dz * dz;
    }

    private static long measureLong(String name, LongSupplier calculation) {
        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
            consume(calculation.getAsLong());
        }

        long[] samples = new long[MEASUREMENT_ITERATIONS];
        for (int iteration = 0; iteration < MEASUREMENT_ITERATIONS; iteration++) {
            long start = System.nanoTime();
            consume(calculation.getAsLong());
            samples[iteration] = System.nanoTime() - start;
        }

        Arrays.sort(samples);
        long median = samples[samples.length / 2];
        System.out.printf(Locale.ROOT, "sample=%s median_ns=%d%n", name, median);
        return median;
    }

    private static long measureChunk(String name, Supplier<List<ChunkBenchmarkPlayer>> query) {
        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
            consumeChunk(query.get());
        }

        long[] samples = new long[MEASUREMENT_ITERATIONS];
        for (int iteration = 0; iteration < MEASUREMENT_ITERATIONS; iteration++) {
            long start = System.nanoTime();
            consumeChunk(query.get());
            samples[iteration] = System.nanoTime() - start;
        }

        java.util.Arrays.sort(samples);
        long median = samples[samples.length / 2];
        System.out.printf(Locale.ROOT, "sample=%s median_ns=%d%n", name, median);
        return median;
    }

    private static List<ChunkBenchmarkPlayer> vanillaChunkQuery(
        List<ChunkBenchmarkPlayer> players,
        double radiusSquared
    ) {
        List<ChunkBenchmarkPlayer> result = new ArrayList<>();
        for (ChunkBenchmarkPlayer player : players) {
            if (player.spectator) {
                continue;
            }

            double dx = player.x;
            double dz = player.z;
            if (dx * dx + dz * dz < radiusSquared) {
                result.add(player);
            }
        }
        return result;
    }

    private static List<ChunkBenchmarkPlayer> berylliumChunkQuery(
        List<ChunkBenchmarkPlayer> players,
        double radiusSquared
    ) {
        return ChunkDistanceSearch.filterWithinExclusiveDistance(
            players,
            0.0,
            0.0,
            radiusSquared,
            player -> !player.spectator,
            player -> player.x,
            player -> player.z
        );
    }

    private static List<ChunkBenchmarkPlayer> createChunkPlayers(int count) {
        List<ChunkBenchmarkPlayer> players = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double x = ((index * 37) % 513) - 256.0;
            double z = ((index * 97) % 513) - 256.0;
            players.add(new ChunkBenchmarkPlayer(index, x, z, index % 5 == 0));
        }
        return players;
    }

    private static double[] createFilterPositions(int count) {
        double[] positions = new double[count * 3];
        for (int index = 0; index < count; index++) {
            int offset = index * 3;
            positions[offset] = (index * 37 % 1021) - 510.0;
            positions[offset + 1] = (index * 53 % 257) - 128.0;
            positions[offset + 2] = (index * 97 % 1021) - 510.0;
        }
        return positions;
    }

    private static double[] createVariableRadiiSquared(int count) {
        double[] radiiSquared = new double[count];
        for (int index = 0; index < count; index++) {
            double radius = 96.0 + index % 5 * 48.0;
            radiiSquared[index] = radius * radius;
        }
        return radiiSquared;
    }

    private static double[] createFilterBoxes(int count) {
        double[] positions = createFilterPositions(count);
        double[] boxes = new double[count * 6];
        for (int index = 0; index < count; index++) {
            int positionOffset = index * 3;
            int boxOffset = index * 6;
            double halfWidth = 0.25 + index % 4 * 0.25;
            boxes[boxOffset] = positions[positionOffset] - halfWidth;
            boxes[boxOffset + 1] = positions[positionOffset + 1] - halfWidth;
            boxes[boxOffset + 2] = positions[positionOffset + 2] - halfWidth;
            boxes[boxOffset + 3] = positions[positionOffset] + halfWidth;
            boxes[boxOffset + 4] = positions[positionOffset + 1] + halfWidth;
            boxes[boxOffset + 5] = positions[positionOffset + 2] + halfWidth;
        }
        return boxes;
    }

    private static Optional<BenchmarkPoint> vanillaQuery(List<BenchmarkPoint> source) {
        List<BenchmarkPoint> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingDouble(point -> squaredDistance(point, 0.0, 0.0, 0.0)));
        for (BenchmarkPoint point : sorted) {
            if (point.wanted && squaredDistance(point, 0.0, 0.0, 0.0) < RADIUS * RADIUS && point.visible) {
                return Optional.of(point);
            }
        }
        return Optional.empty();
    }

    /** Mirrors the Beryllium path before the fused radius-prefix kernel. */
    private static Optional<BenchmarkPoint> legacyNativeQuery(List<BenchmarkPoint> source) {
        double[] positions = EntityPacking.packPositions(
            source,
            BenchmarkPoint::x,
            BenchmarkPoint::y,
            BenchmarkPoint::z
        );
        int[] order = NativeBridge.sortByDistance(0.0, 0.0, 0.0, positions);
        double radiusSquared = RADIUS * RADIUS;
        for (int index : order) {
            BenchmarkPoint point = source.get(index);
            if (point.wanted && squaredPackedDistance(positions, index) < radiusSquared && point.visible) {
                return Optional.of(point);
            }
        }
        return Optional.empty();
    }

    private static Optional<BenchmarkPoint> fusedNativeQuery(List<BenchmarkPoint> source) {
        double[] positions = EntityPacking.packPositions(
            source,
            BenchmarkPoint::x,
            BenchmarkPoint::y,
            BenchmarkPoint::z
        );
        int[] order = new int[source.size()];
        int withinRadiusCount = NativeBridge.sortByDistanceAndCountWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            RADIUS * RADIUS,
            positions,
            order
        );
        for (int orderIndex = 0; orderIndex < order.length; orderIndex++) {
            BenchmarkPoint point = source.get(order[orderIndex]);
            if (point.wanted && orderIndex < withinRadiusCount && point.visible) {
                return Optional.of(point);
            }
        }
        return Optional.empty();
    }

    private static Optional<BenchmarkPoint> topKNativeQuery(List<BenchmarkPoint> source) {
        return EntityDistanceSort.findFirstSortedByDistanceWithinExclusiveDistanceAfterPredicate(
            source,
            0.0,
            0.0,
            0.0,
            RADIUS,
            point -> point.wanted,
            point -> point.visible,
            BenchmarkPoint::x,
            BenchmarkPoint::y,
            BenchmarkPoint::z
        );
    }

    private static List<BenchmarkPoint> createPoints(int count) {
        List<BenchmarkPoint> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double x = ((index * 37) % 401) - 200.0;
            double y = ((index * 67) % 161) - 80.0;
            double z = ((index * 97) % 401) - 200.0;
            boolean wanted = index % 5 != 0;
            boolean visible = index % 7 != 0;
            points.add(new BenchmarkPoint(index, x, y, z, wanted, visible));
        }
        return points;
    }

    private static double squaredDistance(BenchmarkPoint point, double originX, double originY, double originZ) {
        double dx = point.x - originX;
        double dy = point.y - originY;
        double dz = point.z - originZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double squaredPackedDistance(double[] positions, int index) {
        int offset = index * 3;
        double dx = positions[offset];
        double dy = positions[offset + 1];
        double dz = positions[offset + 2];
        return dx * dx + dy * dy + dz * dz;
    }

    private static void consume(Optional<BenchmarkPoint> result) {
        blackHole = blackHole * 31 + result.map(BenchmarkPoint::id).orElse(-1);
    }

    private static void consume(double result) {
        blackHole = blackHole * 31 + Double.doubleToLongBits(result);
    }

    private static void consume(long result) {
        blackHole = blackHole * 31 + result;
    }

    private static void consumeChunk(List<ChunkBenchmarkPlayer> result) {
        blackHole = blackHole * 31 + result.size();
        for (ChunkBenchmarkPlayer player : result) {
            blackHole = blackHole * 31 + player.id;
        }
    }

    private static double speedup(long baselineNanos, long candidateNanos) {
        return (double) baselineNanos / candidateNanos;
    }

    private record BenchmarkPoint(int id, double x, double y, double z, boolean wanted, boolean visible) {
    }

    private record ChunkBenchmarkPlayer(int id, double x, double z, boolean spectator) {
    }
}
