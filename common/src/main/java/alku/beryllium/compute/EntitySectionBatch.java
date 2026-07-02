package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Batched helpers for EntitySection entity queries.
 */
public final class EntitySectionBatch {
    private EntitySectionBatch() {
    }

    public static <T extends EntityAccess> AbortableIterationConsumer.Continuation acceptIntersecting(
        List<? extends T> entities,
        AABB box,
        AbortableIterationConsumer<? super T> consumer
    ) {
        int[] matches = intersectingEntityIndices(box, entities);
        for (int index : matches) {
            if (consumer.accept(entities.get(index)).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }

        return AbortableIterationConsumer.Continuation.CONTINUE;
    }

    public static <T extends EntityAccess, U extends T> AbortableIterationConsumer.Continuation acceptIntersecting(
        Iterable<? extends T> candidates,
        EntityTypeTest<T, U> entityTypeTest,
        AABB box,
        AbortableIterationConsumer<? super U> consumer
    ) {
        List<U> entities = new ArrayList<>();
        for (T candidate : candidates) {
            U castCandidate = entityTypeTest.tryCast(candidate);
            if (castCandidate != null) {
                entities.add(castCandidate);
            }
        }

        if (entities.isEmpty()) {
            return AbortableIterationConsumer.Continuation.CONTINUE;
        }

        int[] matches = intersectingEntityIndices(box, entities);
        for (int index : matches) {
            if (consumer.accept(entities.get(index)).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }

        return AbortableIterationConsumer.Continuation.CONTINUE;
    }

    public static <T extends EntityAccess> int[] intersectingEntityIndices(AABB box, List<? extends T> entities) {
        double[] boxes = EntityBoxPacking.packBoxes(entities);
        return NativeBridge.filterIntersectingAabb(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, boxes);
    }
}
