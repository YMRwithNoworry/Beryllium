package alku.beryllium.compute;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class BlockDistanceSortVerifier {
    private BlockDistanceSortVerifier() {
    }

    public static void verifySortByBlockDistance() {
        List<SimpleBlock> blocks = descendingAxisBlocks(40);

        BlockDistanceSort.sortByDistance(blocks, BlockPos.ZERO, block -> block.position);

        assertListEquals(descendingRange(39, 0), ids(blocks), "block distance sort order");
    }

    public static void verifySortByBlockDistanceTieOrder() {
        List<SimpleBlock> blocks = new ArrayList<>();
        blocks.add(new SimpleBlock(0, new BlockPos(1, 0, 0)));
        blocks.add(new SimpleBlock(1, new BlockPos(-1, 0, 0)));
        blocks.add(new SimpleBlock(2, new BlockPos(2, 0, 0)));

        BlockDistanceSort.sortByDistance(blocks, BlockPos.ZERO, block -> block.position);

        assertListEquals(List.of(0, 1, 2), ids(blocks), "block distance sort tie order");
    }

    private static List<SimpleBlock> descendingAxisBlocks(int count) {
        List<SimpleBlock> blocks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            blocks.add(new SimpleBlock(index, new BlockPos(count - 1 - index, 0, 0)));
        }
        return blocks;
    }

    private static List<Integer> ids(List<SimpleBlock> blocks) {
        List<Integer> result = new ArrayList<>(blocks.size());
        for (SimpleBlock block : blocks) {
            result.add(block.id);
        }
        return result;
    }

    private static List<Integer> descendingRange(int startInclusive, int endInclusive) {
        List<Integer> result = new ArrayList<>(startInclusive - endInclusive + 1);
        for (int value = startInclusive; value >= endInclusive; value--) {
            result.add(value);
        }
        return result;
    }

    private static void assertListEquals(List<Integer> expected, List<Integer> actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private record SimpleBlock(int id, BlockPos position) {
    }
}
