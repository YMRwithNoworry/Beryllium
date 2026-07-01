package alku.beryllium.compute;

import net.minecraft.core.BlockPos;

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

    private static void assertEquals(BlockPos expected, BlockPos actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }
}
