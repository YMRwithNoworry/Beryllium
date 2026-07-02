package alku.beryllium.compute;

import alku.beryllium.mixin.NearestVisibleLivingEntitiesAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.animal.frog.Frog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Preserves FrogAttackablesSensor predicate order while batching its final distance check.
 */
public final class FrogAttackableSearch {
    private static final double TARGET_DETECTION_DISTANCE = 10.0;

    private FrogAttackableSearch() {
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
        return EntityDistancePredicateSearch.findFirstWithinExclusiveDistance(
            candidates,
            source,
            TARGET_DETECTION_DISTANCE,
            candidate -> isMatchingBeforeDistance(source, candidate),
            lineOfSightTest
        );
    }

    private static boolean isMatchingBeforeDistance(LivingEntity source, LivingEntity target) {
        if (source.getBrain().hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN)) {
            return false;
        }
        if (!Sensor.isEntityAttackable(source, target)) {
            return false;
        }
        if (!Frog.canEat(target)) {
            return false;
        }

        return !isUnreachableAttackTarget(source, target);
    }

    private static boolean isUnreachableAttackTarget(LivingEntity source, LivingEntity target) {
        List<UUID> unreachableTargets = source.getBrain()
            .getMemory(MemoryModuleType.UNREACHABLE_TONGUE_TARGETS)
            .orElseGet(List::of);
        return unreachableTargets.contains(target.getUUID());
    }
}
