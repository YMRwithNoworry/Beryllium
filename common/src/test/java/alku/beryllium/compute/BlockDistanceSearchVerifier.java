package alku.beryllium.compute;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class BlockDistanceSearchVerifier {
    private BlockDistanceSearchVerifier() {
    }

    public static void verifyFindNearestByBlockDistance() {
        List<SimpleBlock> blocks = descendingAxisBlocks(40);

        SimpleBlock nearest = BlockDistanceSearch.findNearestByDistance(blocks, BlockPos.ZERO, block -> block.position);

        if (nearest == null || nearest.id != 39) {
            throw new AssertionError("block distance nearest mismatch, expected 39 but got " + (nearest == null ? "null" : nearest.id));
        }
    }

    public static void verifyFindNearestByBlockDistanceTieOrder() {
        List<SimpleBlock> blocks = new ArrayList<>();
        blocks.add(new SimpleBlock(0, new BlockPos(1, 0, 0)));
        blocks.add(new SimpleBlock(1, new BlockPos(-1, 0, 0)));
        blocks.add(new SimpleBlock(2, new BlockPos(2, 0, 0)));

        SimpleBlock nearest = BlockDistanceSearch.findNearestByDistance(blocks, BlockPos.ZERO, block -> block.position);

        if (nearest == null || nearest.id != 0) {
            throw new AssertionError("block distance nearest tie order mismatch, expected 0 but got " + (nearest == null ? "null" : nearest.id));
        }
    }

    public static void verifyFindNearestPositionByBlockDistancePreservesPredicateOrder() {
        List<SimpleBlock> blocks = new ArrayList<>();
        blocks.add(new SimpleBlock(0, new BlockPos(8, 0, 0)));
        blocks.add(new SimpleBlock(1, new BlockPos(1, 0, 0)));
        blocks.add(new SimpleBlock(2, new BlockPos(2, 0, 0)));
        blocks.add(new SimpleBlock(3, new BlockPos(3, 0, 0)));

        List<Integer> mapped = new ArrayList<>();
        List<Integer> tested = new ArrayList<>();

        BlockPos nearest = BlockDistanceSearch.findNearestPositionByDistance(
            blocks,
            BlockPos.ZERO,
            block -> {
                mapped.add(block.id);
                return block.position;
            },
            position -> {
                int id = idForPosition(blocks, position);
                tested.add(id);
                return id % 2 == 0;
            }
        );

        assertEquals(new BlockPos(2, 0, 0), nearest, "filtered block distance nearest");
        assertListEquals(List.of(0, 1, 2, 3), mapped, "filtered block distance mapping order");
        assertListEquals(List.of(0, 1, 2, 3), tested, "filtered block distance predicate order");
    }

    public static void verifyFindNearestPositionByBlockDistanceTieOrder() {
        List<SimpleBlock> blocks = new ArrayList<>();
        blocks.add(new SimpleBlock(0, new BlockPos(1, 0, 0)));
        blocks.add(new SimpleBlock(1, new BlockPos(-1, 0, 0)));
        blocks.add(new SimpleBlock(2, new BlockPos(2, 0, 0)));

        BlockPos nearest = BlockDistanceSearch.findNearestPositionByDistance(
            blocks,
            BlockPos.ZERO,
            block -> block.position,
            position -> true
        );

        assertEquals(new BlockPos(1, 0, 0), nearest, "filtered block distance tie order");
    }

    public static void verifyFindNearestWithinInclusiveBlockDistanceFiltersBeforePostPredicate() {
        List<SimpleBlock> blocks = new ArrayList<>();
        blocks.add(new SimpleBlock(0, new BlockPos(3, 0, 0)));
        blocks.add(new SimpleBlock(1, new BlockPos(2, 0, 0)));
        blocks.add(new SimpleBlock(2, new BlockPos(1, 0, 0)));

        List<Integer> tested = new ArrayList<>();
        SimpleBlock nearest = BlockDistanceSearch.findNearestByDistanceWithinInclusiveRadius(
            blocks,
            BlockPos.ZERO,
            2,
            block -> block.position,
            block -> {
                tested.add(block.id);
                return block.id != 1;
            }
        );

        assertEquals(2, nearest == null ? -1 : nearest.id, "inclusive radius nearest block");
        assertListEquals(List.of(1, 2), tested, "inclusive radius post-distance predicate order");
    }

    public static void verifyFindNearestWithinInclusiveBlockDistanceBatchesRadiusBeforePostPredicate() {
        List<SimpleBlock> blocks = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            blocks.add(new SimpleBlock(index, new BlockPos(100 + index, 0, 0)));
        }
        blocks.set(33, new SimpleBlock(33, new BlockPos(2, 0, 0)));
        blocks.set(34, new SimpleBlock(34, new BlockPos(-2, 0, 0)));
        blocks.set(35, new SimpleBlock(35, new BlockPos(1, 0, 0)));

        List<Integer> tested = new ArrayList<>();
        SimpleBlock nearest = BlockDistanceSearch.findNearestByDistanceWithinInclusiveRadius(
            blocks,
            BlockPos.ZERO,
            2,
            block -> block.position,
            block -> {
                tested.add(block.id);
                return block.id != 33;
            }
        );

        assertEquals(35, nearest == null ? -1 : nearest.id, "large inclusive radius nearest block");
        assertListEquals(List.of(33, 34, 35), tested, "large inclusive radius post-distance predicate order");
    }

    public static void verifyFindNearestPositionWithinInclusiveBlockDistanceTieOrder() {
        List<SimpleBlock> blocks = new ArrayList<>();
        blocks.add(new SimpleBlock(0, new BlockPos(3, 0, 0)));
        blocks.add(new SimpleBlock(1, new BlockPos(1, 0, 0)));
        blocks.add(new SimpleBlock(2, new BlockPos(-1, 0, 0)));

        BlockPos nearest = BlockDistanceSearch.findNearestPositionByDistanceWithinInclusiveRadius(
            blocks,
            BlockPos.ZERO,
            1,
            block -> block.position,
            position -> true
        );

        assertEquals(new BlockPos(1, 0, 0), nearest, "filtered inclusive radius block tie order");
    }

    private static List<SimpleBlock> descendingAxisBlocks(int count) {
        List<SimpleBlock> blocks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            blocks.add(new SimpleBlock(index, new BlockPos(count - 1 - index, 0, 0)));
        }
        return blocks;
    }

    private static int idForPosition(List<SimpleBlock> blocks, BlockPos position) {
        for (SimpleBlock block : blocks) {
            if (block.position.equals(position)) {
                return block.id;
            }
        }

        throw new AssertionError("Unknown block position: " + position);
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

    private static void assertListEquals(List<Integer> expected, List<Integer> actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private record SimpleBlock(int id, BlockPos position) {
    }
}
