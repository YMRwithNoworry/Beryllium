package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import com.google.common.collect.Comparators;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Regression checks for PlayerChunkSender's packed chunk Top-K selection.
 */
public final class ChunkSendBatchSelectorVerifier {
    private static final Field KEY_TABLE_FIELD = field("key");
    private static final Field CONTAINS_NULL_FIELD = field("containsNull");

    private ChunkSendBatchSelectorVerifier() {
    }

    public static void verifyJavaKernelMatchesGuavaTopK() {
        long[] positions = {
            pack(12, -4),
            pack(1, 1),
            pack(-7, 3),
            pack(2, -1),
            pack(30, 30),
            pack(-2, -2)
        };

        assertSelectionMatchesGuava(3, -2, positions, 3, false, "Java exact Top-K");
    }

    public static void verifyJavaKernelLimitBoundaries() {
        long[] positions = {pack(3, 0), pack(1, 0), pack(2, 0)};

        assertSelectionMatchesGuava(0, 0, positions, 0, false, "Java zero limit");
        assertSelectionMatchesGuava(0, 0, positions, positions.length, false, "Java full limit");
        assertSelectionMatchesGuava(0, 0, positions, positions.length + 4, false, "Java oversized limit");
    }

    public static void verifyJavaKernelPreservesWrappingIntDistance() {
        long[] positions = {
            pack(Integer.MIN_VALUE, Integer.MAX_VALUE),
            pack(Integer.MAX_VALUE, Integer.MIN_VALUE),
            pack(0, 0),
            pack(Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 1),
            pack(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1)
        };

        assertSelectionMatchesGuava(
            Integer.MAX_VALUE,
            Integer.MIN_VALUE,
            positions,
            3,
            false,
            "Java wrapping distance"
        );
    }

    public static void verifyJavaKernelMatchesGuavaTieBehavior() {
        long[] positions = {
            pack(5, 0),
            pack(1, 0),
            pack(0, 1),
            pack(-1, 0),
            pack(0, -1),
            pack(4, 3),
            pack(-3, 4),
            pack(3, -4),
            pack(-4, -3),
            pack(2, 0),
            pack(0, 2),
            pack(-2, 0),
            pack(0, -2)
        };

        assertSelectionMatchesGuava(0, 0, positions, 6, false, "Java tied Top-K");
    }

    public static void verifyJavaKernelLeavesOutputTailUntouched() {
        long[] positions = {pack(8, 8), pack(1, 0), pack(0, 2), pack(-3, 0)};
        int[] output = new int[7];
        Arrays.fill(output, 73);

        int count = JavaComputeKernels.selectNearestChunkIndices(0, 0, positions, 2, output);

        assertEquals(2, count, "Java output count");
        assertArrayEquals(new int[] {1, 2}, Arrays.copyOf(output, count), "Java output prefix");
        assertArrayEquals(new int[] {73, 73, 73, 73, 73}, Arrays.copyOfRange(output, count, output.length), "Java output tail");
    }

    public static void verifyJavaKernelLargeBatchMatchesGuava() {
        assertSelectionMatchesGuava(91, -37, createLargePositions(8192), 64, false, "Java large Top-K");
    }

    public static void verifyBridgeMatchesGuavaTopK() {
        assertSelectionMatchesGuava(17, -29, createLargePositions(8192), 64, true, "bridge large Top-K");
    }

    public static void verifySelectorFacadeMatchesGuavaTopK() {
        long[] positions = createLargePositions(8192);
        int[] expected = guavaSelection(17, -29, positions, 64);
        int[] actual = ChunkSendBatchSelector.selectNearestChunkIndices(17, -29, positions, 64);
        assertArrayEquals(expected, actual, "selector facade large Top-K");
    }

    public static void verifyFastutilPrimitiveStreamPreservesBoxedStreamOrder() {
        LongOpenHashSet pendingChunks = new LongOpenHashSet();
        for (long position : createLargePositions(257)) {
            pendingChunks.add(position);
        }
        pendingChunks.add(0L);

        long[] streamOrder = pendingChunks.stream().mapToLong(Long::longValue).toArray();
        long[] snapshotOrder = pendingChunks.longStream().toArray();
        if (!Arrays.equals(streamOrder, snapshotOrder)) {
            throw new AssertionError("FastUtil primitive stream order differs from vanilla boxed stream encounter order");
        }

        ChunkSendBatchSelector.Scratch scratch = ChunkSendBatchSelector.acquireScratch(pendingChunks.size(), 64);
        try {
            int snapshotSize = scratch.snapshot(keyTable(pendingChunks), containsNull(pendingChunks));
            assertArrayEquals(
                streamOrder,
                Arrays.copyOf(scratch.packedChunkPositions(), snapshotSize),
                "scratch table encounter order"
            );
        } finally {
            ChunkSendBatchSelector.releaseScratch(scratch);
        }
    }

    public static void verifyScratchReuseIgnoresUnusedCapacity() {
        LongOpenHashSet largePendingChunks = new LongOpenHashSet(createLargePositions(16384));
        ChunkSendBatchSelector.Scratch largeScratch = ChunkSendBatchSelector.acquireScratch(
            largePendingChunks.size(),
            64
        );
        try {
            largeScratch.snapshot(keyTable(largePendingChunks), containsNull(largePendingChunks));
        } finally {
            ChunkSendBatchSelector.releaseScratch(largeScratch);
        }

        LongOpenHashSet pendingChunks = new LongOpenHashSet(createLargePositions(8192));
        ChunkSendBatchSelector.Scratch scratch = ChunkSendBatchSelector.acquireScratch(pendingChunks.size(), 64);
        try {
            int candidateCount = scratch.snapshot(keyTable(pendingChunks), containsNull(pendingChunks));
            long[] packedChunkPositions = scratch.packedChunkPositions();
            if (packedChunkPositions.length <= candidateCount) {
                throw new AssertionError("expected the smaller snapshot to reuse a larger packed buffer");
            }
            Arrays.fill(packedChunkPositions, candidateCount, packedChunkPositions.length, pack(0, 0));

            int[] expected = guavaSelection(
                0,
                0,
                Arrays.copyOf(packedChunkPositions, candidateCount),
                64
            );
            int[] output = scratch.selectedIndices();
            int actualCount = ChunkSendBatchSelector.selectNearestChunkIndices(
                0,
                0,
                packedChunkPositions,
                candidateCount,
                64,
                output
            );

            assertEquals(expected.length, actualCount, "scratch prefix selection count");
            assertArrayEquals(expected, Arrays.copyOf(output, actualCount), "scratch prefix selection indices");
        } finally {
            ChunkSendBatchSelector.releaseScratch(scratch);
        }
    }

    public static void verifyScratchPoolIsReentrant() {
        ChunkSendBatchSelector.Scratch outer = ChunkSendBatchSelector.acquireScratch(8192, 64);
        try {
            outer.packedChunkPositions()[0] = pack(17, -29);
            verifyCrossThreadReleaseFails(outer);
            ChunkSendBatchSelector.Scratch nested = ChunkSendBatchSelector.acquireScratch(8192, 64);
            ChunkSendBatchSelector.Scratch deepNested;
            try {
                if (nested == outer) {
                    throw new AssertionError("nested chunk send selection reused the active outer scratch");
                }
                nested.packedChunkPositions()[0] = pack(-91, 37);
                deepNested = ChunkSendBatchSelector.acquireScratch(8192, 64);
                try {
                    if (deepNested == outer || deepNested == nested) {
                        throw new AssertionError("deeply nested chunk send selection reused an active scratch");
                    }
                } finally {
                    ChunkSendBatchSelector.releaseScratch(deepNested);
                }

                boolean outOfOrderReleaseFailed = false;
                try {
                    ChunkSendBatchSelector.releaseScratch(outer);
                } catch (IllegalStateException expected) {
                    outOfOrderReleaseFailed = true;
                }
                if (!outOfOrderReleaseFailed) {
                    throw new AssertionError("out-of-order scratch release must fail");
                }
            } finally {
                ChunkSendBatchSelector.releaseScratch(nested);
            }

            ChunkSendBatchSelector.Scratch reusedNested = ChunkSendBatchSelector.acquireScratch(8192, 64);
            try {
                if (reusedNested != nested) {
                    throw new AssertionError("released nested chunk send scratch was not reused");
                }
                ChunkSendBatchSelector.Scratch reusedDeepNested = ChunkSendBatchSelector.acquireScratch(8192, 64);
                try {
                    if (reusedDeepNested != deepNested) {
                        throw new AssertionError("released deeply nested chunk send scratch was not reused");
                    }
                } finally {
                    ChunkSendBatchSelector.releaseScratch(reusedDeepNested);
                }
            } finally {
                ChunkSendBatchSelector.releaseScratch(reusedNested);
            }

            if (outer.packedChunkPositions()[0] != pack(17, -29)) {
                throw new AssertionError("nested chunk send selection overwrote the outer scratch");
            }
        } finally {
            ChunkSendBatchSelector.releaseScratch(outer);
        }

        ChunkSendBatchSelector.Scratch reusedOuter = ChunkSendBatchSelector.acquireScratch(8192, 64);
        try {
            if (reusedOuter != outer) {
                throw new AssertionError("released primary chunk send scratch was not reused");
            }
        } finally {
            ChunkSendBatchSelector.releaseScratch(reusedOuter);
        }

        boolean unmatchedReleaseFailed = false;
        try {
            ChunkSendBatchSelector.releaseScratch(null);
        } catch (IllegalStateException expected) {
            unmatchedReleaseFailed = true;
        }
        if (!unmatchedReleaseFailed) {
            throw new AssertionError("scratch release without a matching acquire must fail");
        }
    }

    private static void verifyCrossThreadReleaseFails(ChunkSendBatchSelector.Scratch scratch) {
        Throwable[] failure = {null};
        Thread thread = new Thread(() -> {
            try {
                ChunkSendBatchSelector.releaseScratch(scratch);
            } catch (Throwable thrown) {
                failure[0] = thrown;
            }
        }, "beryllium-scratch-release-verifier");
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while verifying cross-thread scratch release", interrupted);
        }
        if (!(failure[0] instanceof IllegalStateException)) {
            throw new AssertionError("cross-thread scratch release must fail with IllegalStateException", failure[0]);
        }
    }

    public static void verifyBridgeLeavesOutputTailUntouched() {
        long[] positions = {pack(8, 8), pack(1, 0), pack(0, 2), pack(-3, 0)};
        int[] output = new int[7];
        Arrays.fill(output, 91);

        int count = NativeBridge.selectNearestChunkIndices(0, 0, positions, 2, output);

        assertEquals(2, count, "bridge output count");
        assertArrayEquals(new int[] {1, 2}, Arrays.copyOf(output, count), "bridge output prefix");
        assertArrayEquals(new int[] {91, 91, 91, 91, 91}, Arrays.copyOfRange(output, count, output.length), "bridge output tail");
    }

    public static void verifyBridgeRandomizedMatchesGuava() {
        Random random = new Random(0xB3E711L);
        for (int trial = 0; trial < 200; trial++) {
            int candidateCount = random.nextInt(513);
            long[] positions = new long[candidateCount];
            for (int index = 0; index < candidateCount; index++) {
                positions[index] = pack(random.nextInt(), random.nextInt());
            }
            int limit = random.nextInt(candidateCount + 17);
            assertSelectionMatchesGuava(
                random.nextInt(),
                random.nextInt(),
                positions,
                limit,
                true,
                "bridge randomized Top-K trial " + trial
            );
        }
    }

    private static void assertSelectionMatchesGuava(
        int originX,
        int originZ,
        long[] positions,
        int limit,
        boolean bridge,
        String label
    ) {
        int expectedCount = Math.min(limit, positions.length);
        int[] expected = guavaSelection(originX, originZ, positions, limit);
        int[] output = new int[expectedCount];
        int actualCount = bridge
            ? NativeBridge.selectNearestChunkIndices(originX, originZ, positions, limit, output)
            : JavaComputeKernels.selectNearestChunkIndices(originX, originZ, positions, limit, output);

        assertEquals(expectedCount, actualCount, label + " count");
        assertArrayEquals(expected, output, label + " indices");
    }

    private static int[] guavaSelection(int originX, int originZ, long[] positions, int limit) {
        List<Candidate> candidates = new ArrayList<>(positions.length);
        for (int index = 0; index < positions.length; index++) {
            candidates.add(new Candidate(index, positions[index]));
        }

        return candidates.stream()
            .collect(Comparators.least(
                limit,
                Comparator.comparingInt(candidate -> distanceSquared(originX, originZ, candidate.packedPosition()))
            ))
            .stream()
            .mapToInt(Candidate::index)
            .toArray();
    }

    private static long[] createLargePositions(int count) {
        long[] positions = new long[count];
        for (int index = 0; index < count; index++) {
            int x = index * 1103515245 + 12345;
            int z = Integer.rotateLeft(index * 0x9E3779B9, index & 31);
            positions[index] = pack(x, z);
        }
        return positions;
    }

    private static int distanceSquared(int originX, int originZ, long packedPosition) {
        int dx = (int) packedPosition - originX;
        int dz = (int) (packedPosition >>> 32) - originZ;
        return dx * dx + dz * dz;
    }

    private static long pack(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    private static Field field(String name) {
        try {
            Field field = LongOpenHashSet.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static long[] keyTable(LongOpenHashSet values) {
        try {
            return (long[]) KEY_TABLE_FIELD.get(values);
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Unable to read FastUtil key table", failure);
        }
    }

    private static boolean containsNull(LongOpenHashSet values) {
        try {
            return CONTAINS_NULL_FIELD.getBoolean(values);
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Unable to read FastUtil null-key state", failure);
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private static void assertArrayEquals(int[] expected, int[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + " mismatch, expected " + Arrays.toString(expected) + " but got " + Arrays.toString(actual));
        }
    }

    private static void assertArrayEquals(long[] expected, long[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + " mismatch, expected " + Arrays.toString(expected) + " but got " + Arrays.toString(actual));
        }
    }

    private record Candidate(int index, long packedPosition) {
    }
}
