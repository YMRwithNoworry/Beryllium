package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import com.google.common.collect.Comparators;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Regression checks for PlayerChunkSender's packed chunk Top-K selection.
 */
public final class ChunkSendBatchSelectorVerifier {
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
        LongSet pendingChunks = new LongOpenHashSet();
        for (long position : createLargePositions(257)) {
            pendingChunks.add(position);
        }

        long[] streamOrder = pendingChunks.stream().mapToLong(Long::longValue).toArray();
        long[] snapshotOrder = pendingChunks.longStream().toArray();
        if (!Arrays.equals(streamOrder, snapshotOrder)) {
            throw new AssertionError("FastUtil primitive stream order differs from vanilla boxed stream encounter order");
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

    private record Candidate(int index, long packedPosition) {
    }
}
