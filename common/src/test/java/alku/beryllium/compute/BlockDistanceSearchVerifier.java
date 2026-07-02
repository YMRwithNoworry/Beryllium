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

    private static List<SimpleBlock> descendingAxisBlocks(int count) {
        List<SimpleBlock> blocks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            blocks.add(new SimpleBlock(index, new BlockPos(count - 1 - index, 0, 0)));
        }
        return blocks;
    }

    private record SimpleBlock(int id, BlockPos position) {
    }
}
