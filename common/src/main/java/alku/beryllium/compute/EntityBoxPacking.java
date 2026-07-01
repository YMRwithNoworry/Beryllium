package alku.beryllium.compute;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Packs entity bounding boxes into primitive arrays for batch AABB tests.
 */
public final class EntityBoxPacking {
    private EntityBoxPacking() {
    }

    public static double[] packBoxes(List<? extends EntityAccess> entities) {
        double[] boxes = new double[entities.size() * 6];
        for (int index = 0; index < entities.size(); index++) {
            AABB box = entities.get(index).getBoundingBox();
            int offset = index * 6;
            boxes[offset] = box.minX;
            boxes[offset + 1] = box.minY;
            boxes[offset + 2] = box.minZ;
            boxes[offset + 3] = box.maxX;
            boxes[offset + 4] = box.maxY;
            boxes[offset + 5] = box.maxZ;
        }
        return boxes;
    }
}
