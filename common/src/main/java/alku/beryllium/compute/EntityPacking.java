package alku.beryllium.compute;

import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Packs entity coordinates into primitive arrays for batch distance evaluation.
 */
public final class EntityPacking {
    private EntityPacking() {
    }

    public static double[] packPositions(List<? extends Entity> entities) {
        return packPositions(entities, Entity::getX, Entity::getY, Entity::getZ);
    }

    public static <T> double[] packPositions(
        List<? extends T> values,
        CoordinateGetter<? super T> xGetter,
        CoordinateGetter<? super T> yGetter,
        CoordinateGetter<? super T> zGetter
    ) {
        double[] positions = new double[values.size() * 3];
        for (int index = 0; index < values.size(); index++) {
            T value = values.get(index);
            int offset = index * 3;
            positions[offset] = xGetter.get(value);
            positions[offset + 1] = yGetter.get(value);
            positions[offset + 2] = zGetter.get(value);
        }
        return positions;
    }

    @FunctionalInterface
    public interface CoordinateGetter<T> {
        double get(T value);
    }
}
