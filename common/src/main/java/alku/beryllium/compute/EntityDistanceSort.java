package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts entity-like lists by squared distance while preserving vanilla stable tie order.
 */
public final class EntityDistanceSort {
    private EntityDistanceSort() {
    }

    public static <T extends Entity> void sortByDistance(List<T> entities, Entity origin) {
        sortByDistance(
            entities,
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            Entity::getX,
            Entity::getY,
            Entity::getZ
        );
    }

    public static <T> void sortByDistance(
        List<T> values,
        double originX,
        double originY,
        double originZ,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter
    ) {
        if (values.size() < 2) {
            return;
        }

        if (!NativeBatching.shouldUseNativeEntityBatch(values.size())) {
            values.sort(Comparator.comparingDouble(value -> squaredDistance(originX, originY, originZ, xGetter, yGetter, zGetter, value)));
            return;
        }

        double[] positions = EntityPacking.packPositions(values, xGetter, yGetter, zGetter);
        reorder(values, NativeBridge.sortByDistance(originX, originY, originZ, positions));
    }

    private static <T> void reorder(List<T> values, int[] order) {
        List<T> snapshot = new ArrayList<>(values);
        for (int index = 0; index < order.length; index++) {
            values.set(index, snapshot.get(order[index]));
        }
    }

    private static <T> double squaredDistance(
        double originX,
        double originY,
        double originZ,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter,
        T value
    ) {
        double dx = xGetter.get(value) - originX;
        double dy = yGetter.get(value) - originY;
        double dz = zGetter.get(value) - originZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
