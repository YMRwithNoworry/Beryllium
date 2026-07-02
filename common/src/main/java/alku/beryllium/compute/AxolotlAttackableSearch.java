package alku.beryllium.compute;

import alku.beryllium.mixin.NearestVisibleLivingEntitiesAccessor;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.Sensor;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Preserves AxolotlAttackablesSensor predicate order while batching its leading radius check.
 */
public final class AxolotlAttackableSearch {
    private static final double TARGET_DETECTION_DISTANCE = 8.0;

    private AxolotlAttackableSearch() {
    }

    public static Optional<LivingEntity> findNearest(LivingEntity source) {
        Optional<NearestVisibleLivingEntities> visibleEntities = source.getBrain()
            .getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        if (visibleEntities.isEmpty()) {
            return Optional.empty();
        }

        NearestVisibleLivingEntitiesAccessor accessor = (NearestVisibleLivingEntitiesAccessor) visibleEntities.get();
        List<LivingEntity> candidates = accessor.beryllium$nearbyEntities();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Predicate<LivingEntity> lineOfSightTest = accessor.beryllium$lineOfSightTest();
        return EntityDistancePredicateSearch.findFirstWithinInclusiveDistance(
            candidates,
            source,
            TARGET_DETECTION_DISTANCE,
            candidate -> true,
            candidate -> isMatchingAfterDistance(source, candidate) && lineOfSightTest.test(candidate)
        );
    }

    private static boolean isMatchingAfterDistance(LivingEntity source, LivingEntity target) {
        if (!target.isInWaterOrBubble()) {
            return false;
        }

        if (!isHostileTarget(target) && !isHuntTarget(source, target)) {
            return false;
        }

        return Sensor.isEntityAttackable(source, target);
    }

    private static boolean isHuntTarget(LivingEntity source, LivingEntity target) {
        Brain<?> brain = source.getBrain();
        return !brain.hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN)
            && target.getType().is(EntityTypeTags.AXOLOTL_HUNT_TARGETS);
    }

    private static boolean isHostileTarget(LivingEntity target) {
        return target.getType().is(EntityTypeTags.AXOLOTL_ALWAYS_HOSTILES);
    }
}
