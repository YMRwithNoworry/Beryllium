package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Sorts block-position-backed lists by squared distance to a BlockPos low corner.
 */
public final class BlockDistanceSort {
    private BlockDistanceSort() {
    }

    public static <T> void sortByDistance(
        List<T> values,
        BlockPos origin,
        BlockPositionGetter<? super T> positionGetter
    ) {
        if (values.size() < 2) {
            return;
        }

        if (!NativeBatching.shouldUseNativeEntityBatch(values.size())) {
            values.sort(Comparator.comparingDouble(value -> squaredDistance(origin, positionGetter.get(value))));
            return;
        }

        int[] positions = packPositions(values, positionGetter);
        reorder(values, NativeBridge.sortByBlockDistance(origin.getX(), origin.getY(), origin.getZ(), positions));
    }

    public static <T> T findFirstSortedByDistance(
        List<T> values,
        BlockPos origin,
        BlockPositionGetter<? super T> positionGetter,
        Predicate<? super T> postSortPredicate
    ) {
        sortByDistance(values, origin, positionGetter);
        for (T value : values) {
            if (postSortPredicate.test(value)) {
                return value;
            }
        }

        return null;
    }

    private static <T> int[] packPositions(List<? extends T> values, BlockPositionGetter<? super T> positionGetter) {
        int[] positions = new int[values.size() * 3];
        for (int index = 0; index < values.size(); index++) {
            BlockPos position = positionGetter.get(values.get(index));
            int offset = index * 3;
            positions[offset] = position.getX();
            positions[offset + 1] = position.getY();
            positions[offset + 2] = position.getZ();
        }
        return positions;
    }

    private static <T> void reorder(List<T> values, int[] order) {
        List<T> snapshot = new ArrayList<>(values);
        for (int index = 0; index < order.length; index++) {
            values.set(index, snapshot.get(order[index]));
        }
    }

    private static double squaredDistance(BlockPos origin, BlockPos position) {
        double dx = (double) position.getX() - origin.getX();
        double dy = (double) position.getY() - origin.getY();
        double dz = (double) position.getZ() - origin.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    @FunctionalInterface
    public interface BlockPositionGetter<T> {
        BlockPos get(T value);
    }
}
