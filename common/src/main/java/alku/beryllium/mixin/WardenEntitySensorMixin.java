package alku.beryllium.mixin;

import alku.beryllium.compute.EntityDistanceSort;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.WardenEntitySensor;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;
import java.util.function.Predicate;

@Mixin(WardenEntitySensor.class)
public class WardenEntitySensorMixin {
    /**
     * @reason Keep the native nearest-living distance sort and replace two stream passes with ordered loops.
     * @author YMRwithNoworry
     */
    @Overwrite
    protected void doTick(ServerLevel level, Warden warden) {
        AABB box = warden.getBoundingBox().inflate(24.0, 24.0, 24.0);
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            candidate -> candidate != warden && candidate.isAlive()
        );
        EntityDistanceSort.sortByDistance(nearbyEntities, warden);

        Brain<?> brain = warden.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, nearbyEntities);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(warden, nearbyEntities));

        LivingEntity nearestAttackable = beryllium$findClosest(nearbyEntities, warden, candidate -> candidate.getType() != EntityType.PLAYER);
        if (nearestAttackable == null) {
            nearestAttackable = beryllium$findClosest(nearbyEntities, warden, candidate -> candidate.getType() == EntityType.PLAYER);
        }

        if (nearestAttackable == null) {
            brain.eraseMemory(MemoryModuleType.NEAREST_ATTACKABLE);
        } else {
            brain.setMemory(MemoryModuleType.NEAREST_ATTACKABLE, nearestAttackable);
        }
    }

    private static LivingEntity beryllium$findClosest(
        List<LivingEntity> nearbyEntities,
        Warden warden,
        Predicate<LivingEntity> predicate
    ) {
        for (LivingEntity candidate : nearbyEntities) {
            if (warden.canTargetEntity(candidate) && predicate.test(candidate)) {
                return candidate;
            }
        }

        return null;
    }
}
