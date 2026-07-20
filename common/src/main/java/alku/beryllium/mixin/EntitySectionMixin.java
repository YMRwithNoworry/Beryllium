package alku.beryllium.mixin;

import alku.beryllium.compute.EntitySectionBatch;
import alku.beryllium.compute.NativeBatching;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(EntitySection.class)
public class EntitySectionMixin<T extends EntityAccess> {
    /**
     * @reason Batch entity bounding-box intersection tests through the native AABB kernel.
     * @author YMRwithNoworry
     */
    @Overwrite
    public AbortableIterationConsumer.Continuation getEntities(AABB box, AbortableIterationConsumer<T> consumer) {
        ClassInstanceMultiMap<T> storage = ((EntitySectionAccessor<T>) this).beryllium$storage();
        if (!shouldUseNativeBatch(storage.size())) {
            return getEntitiesVanilla(storage, box, consumer);
        }

        return EntitySectionBatch.acceptIntersecting(snapshot(storage), box, consumer);
    }

    /**
     * @reason Batch entity bounding-box intersection tests through the native AABB kernel.
     * @author YMRwithNoworry
     */
    @Overwrite
    public <U extends T> AbortableIterationConsumer.Continuation getEntities(
        EntityTypeTest<T, U> entityTypeTest,
        AABB box,
        AbortableIterationConsumer<? super U> consumer
    ) {
        ClassInstanceMultiMap<T> storage = ((EntitySectionAccessor<T>) this).beryllium$storage();
        Collection<? extends T> candidates = storage.find(entityTypeTest.getBaseClass());
        if (!shouldUseNativeBatch(candidates.size())) {
            return getEntitiesVanilla(candidates, entityTypeTest, box, consumer);
        }

        return EntitySectionBatch.acceptIntersecting(candidates, entityTypeTest, box, consumer);
    }

    private static boolean shouldUseNativeBatch(int candidateCount) {
        return NativeBatching.shouldUseNativeAabbBatch(candidateCount);
    }

    private static <T extends EntityAccess> AbortableIterationConsumer.Continuation getEntitiesVanilla(
        Iterable<? extends T> entities,
        AABB box,
        AbortableIterationConsumer<T> consumer
    ) {
        for (T entity : entities) {
            if (entity.getBoundingBox().intersects(box) && consumer.accept(entity).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }

        return AbortableIterationConsumer.Continuation.CONTINUE;
    }

    private static <T extends EntityAccess, U extends T> AbortableIterationConsumer.Continuation getEntitiesVanilla(
        Iterable<? extends T> entities,
        EntityTypeTest<T, U> entityTypeTest,
        AABB box,
        AbortableIterationConsumer<? super U> consumer
    ) {
        for (T entity : entities) {
            U castEntity = entityTypeTest.tryCast(entity);
            if (castEntity != null && entity.getBoundingBox().intersects(box) && consumer.accept(castEntity).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }

        return AbortableIterationConsumer.Continuation.CONTINUE;
    }

    private static <T> List<T> snapshot(Iterable<? extends T> values) {
        List<T> result = new ArrayList<>();
        for (T value : values) {
            result.add(value);
        }
        return result;
    }
}
