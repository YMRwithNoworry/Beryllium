package alku.beryllium.mixin;

import alku.beryllium.compute.EntityDistanceSort;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.BreezeAttackEntitySensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;

@Mixin(BreezeAttackEntitySensor.class)
public class BreezeAttackEntitySensorMixin {
    /**
     * @reason Keep the native nearest-living distance sort and replace stream filters with an ordered loop.
     * @author YMRwithNoworry
     */
    @Overwrite
    protected void doTick(ServerLevel level, Breeze breeze) {
        AABB box = breeze.getBoundingBox().inflate(24.0, 24.0, 24.0);
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            candidate -> candidate != breeze && candidate.isAlive()
        );
        EntityDistanceSort.sortByDistance(nearbyEntities, breeze);

        Brain<?> brain = breeze.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, nearbyEntities);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(breeze, nearbyEntities));

        for (LivingEntity candidate : nearbyEntities) {
            if (EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(candidate) && Sensor.isEntityAttackable(breeze, candidate)) {
                brain.setMemory(MemoryModuleType.NEAREST_ATTACKABLE, candidate);
                return;
            }
        }

        brain.eraseMemory(MemoryModuleType.NEAREST_ATTACKABLE);
    }
}
