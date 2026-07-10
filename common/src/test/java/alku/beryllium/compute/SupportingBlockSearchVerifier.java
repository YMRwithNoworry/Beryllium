package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SupportingBlockSearchVerifier {
    private SupportingBlockSearchVerifier() {
    }

    public static void verifyFindNearest() {
        List<BlockPos> candidates = List.of(
            new BlockPos(1, 0, 0),
            new BlockPos(-1, 0, 0),
            new BlockPos(0, 1, 0),
            new BlockPos(0, 1, 1)
        );

        Optional<BlockPos> actual = SupportingBlockSearch.findNearest(candidates, 0.5, 0.5, 0.5);
        assertEquals(new BlockPos(0, 1, 0), actual.orElseThrow(), "supporting block tie order");
    }

    public static void verifyEmptyCandidates() {
        Optional<BlockPos> actual = SupportingBlockSearch.findNearest(List.of(), 0.0, 0.0, 0.0);
        if (actual.isPresent()) {
            throw new AssertionError("Expected empty supporting block candidates to return empty, got " + actual);
        }
    }

    public static void verifyFindNearestIgnoresUnusedPackedCapacity() {
        int[] positions = new int[48];
        positions[0] = 10;
        positions[3] = 11;

        int nearestIndex = NativeBridge.findNearestBlockCenterIndex(0.5, 0.5, 0.5, positions, 2);
        assertEquals(0, nearestIndex, "nearest packed prefix index");

        List<BlockPos> candidates = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            candidates.add(new BlockPos(10 + index, 0, 0));
        }

        Optional<BlockPos> actual = SupportingBlockSearch.findNearest(candidates, 0.5, 0.5, 0.5);
        assertEquals(candidates.getFirst(), actual.orElseThrow(), "nearest supporting block with unused packed capacity");
    }

    private static void assertEquals(BlockPos expected, BlockPos actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }
}
