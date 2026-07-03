package alku.beryllium.mixin;

import alku.beryllium.compute.EntityDistanceSort;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;

@Mixin(NearestItemSensor.class)
public class NearestItemSensorMixin {
    /**
     * @reason Batch wanted-item ordering through native distance sort before vanilla filters.
     * @author YMRwithNoworry
     */
    @Overwrite
    protected void doTick(ServerLevel level, Mob mob) {
        Brain<?> brain = mob.getBrain();
        List<ItemEntity> items = level.getEntitiesOfClass(
            ItemEntity.class,
            mob.getBoundingBox().inflate(32.0, 16.0, 32.0),
            item -> true
        );
        var nearestWantedItem = EntityDistanceSort.findFirstSortedByDistanceWithinExclusiveDistanceAfterPredicate(
            items,
            mob.getX(),
            mob.getY(),
            mob.getZ(),
            32.0,
            item -> mob.wantsToPickUp(item.getItem()),
            item -> mob.hasLineOfSight(item),
            ItemEntity::getX,
            ItemEntity::getY,
            ItemEntity::getZ
        );
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, nearestWantedItem);
    }
}
