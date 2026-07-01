package alku.beryllium.mixin;

import alku.beryllium.compute.EntityDistanceSort;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(NearestLivingEntitySensor.class)
public abstract class NearestLivingEntitySensorMixin<T extends LivingEntity> {
    @Shadow
    protected abstract int radiusXZ();

    @Shadow
    protected abstract int radiusY();

    /**
     * @reason Batch nearest-living memory ordering through native distance sort.
     * @author YMRwithNoworry
     */
    @Overwrite
    protected void doTick(ServerLevel level, T entity) {
        AABB box = entity.getBoundingBox().inflate(this.radiusXZ(), this.radiusY(), this.radiusXZ());
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            candidate -> candidate != entity && candidate.isAlive()
        );
        EntityDistanceSort.sortByDistance(nearbyEntities, entity);

        Brain<?> brain = entity.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, nearbyEntities);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(entity, nearbyEntities));
    }
}
