package alku.beryllium.compute;

import alku.beryllium.mixin.TargetingConditionsAccessor;
import alku.beryllium.bridge.NativeBridge;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Evaluates TargetingConditions using precomputed squared distances.
 */
public final class TargetingConditionsBatch {
    private TargetingConditionsBatch() {
    }

    public static boolean pretest(@Nullable LivingEntity source, LivingEntity target, TargetingConditions conditions) {
        if (source == target) {
            return false;
        }
        if (!target.canBeSeenByAnyone()) {
            return false;
        }

        TargetingConditionsAccessor accessor = (TargetingConditionsAccessor) conditions;
        Predicate<LivingEntity> selector = accessor.beryllium$selector();
        if (selector != null && !selector.test(target)) {
            return false;
        }

        if (source == null) {
            return !accessor.beryllium$isCombat() || (target.canBeSeenAsEnemy() && target.level().getDifficulty() != Difficulty.PEACEFUL);
        }

        if (accessor.beryllium$isCombat() && (!source.canAttack(target) || !source.canAttackType(target.getType()) || source.isAlliedTo(target))) {
            return false;
        }

        if (accessor.beryllium$checkLineOfSight() && source instanceof Mob mob && !mob.getSensing().hasLineOfSight(target)) {
            return false;
        }

        return true;
    }

    public static boolean testDistance(@Nullable LivingEntity source, LivingEntity target, TargetingConditions conditions, double distanceSquared) {
        TargetingConditionsAccessor accessor = (TargetingConditionsAccessor) conditions;
        if (source == null) {
            return true;
        }

        double range = accessor.beryllium$range();
        if (range > 0.0) {
            double visibilityPercent = accessor.beryllium$testInvisible() ? target.getVisibilityPercent(source) : 1.0;
            double maxDistance = Math.max(range * visibilityPercent, 2.0);
            if (distanceSquared > maxDistance * maxDistance) {
                return false;
            }
        }

        return true;
    }

    public static <T extends LivingEntity> List<T> filterNearby(
        List<? extends T> candidates,
        TargetingConditions conditions,
        @Nullable LivingEntity source,
        Predicate<? super T> candidatePredicate
    ) {
        if (source == null) {
            return List.copyOf(candidates.stream().filter(candidatePredicate::test).filter(candidate -> pretest(null, candidate, conditions)).toList());
        }

        List<T> filteredCandidates = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            if (candidatePredicate.test(candidate) && pretest(source, candidate, conditions)) {
                filteredCandidates.add(candidate);
            }
        }

        if (filteredCandidates.isEmpty()) {
            return List.of();
        }

        return filterByDistance(filteredCandidates, conditions, source);
    }

    public static <T extends LivingEntity> List<T> filterNearbyInBox(
        List<? extends T> candidates,
        TargetingConditions conditions,
        @Nullable LivingEntity source,
        AABB box
    ) {
        if (source == null) {
            return List.copyOf(candidates.stream()
                .filter(candidate -> box.contains(candidate.getX(), candidate.getY(), candidate.getZ()))
                .filter(candidate -> pretest(null, candidate, conditions))
                .toList());
        }

        double[] positions = EntityPacking.packPositions(candidates);
        int[] boxMatches = positions.length / 3 >= 32 && NativeBridge.isLoaded()
            ? NativeBridge.filterWithinAabb(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, positions)
            : JavaComputeKernels.filterWithinAabb(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, positions);

        if (boxMatches.length == 0) {
            return List.of();
        }

        List<T> boxedCandidates = new ArrayList<>(boxMatches.length);
        for (int index : boxMatches) {
            T candidate = candidates.get(index);
            if (pretest(source, candidate, conditions)) {
                boxedCandidates.add(candidate);
            }
        }

        if (boxedCandidates.isEmpty()) {
            return List.of();
        }

        return filterByDistance(boxedCandidates, conditions, source);
    }

    private static <T extends LivingEntity> List<T> filterByDistance(
        List<T> filteredCandidates,
        TargetingConditions conditions,
        LivingEntity source
    ) {
        double[] positions = EntityPacking.packPositions(filteredCandidates);
        TargetingConditionsAccessor accessor = (TargetingConditionsAccessor) conditions;
        double range = accessor.beryllium$range();
        if (range <= 0.0) {
            return new ArrayList<>(filteredCandidates);
        }

        if (!accessor.beryllium$testInvisible()) {
            double maxDistance = Math.max(range, 2.0);
            int[] matches = positions.length / 3 >= 32 && NativeBridge.isLoaded()
                ? NativeBridge.filterWithinRadius(source.getX(), source.getY(), source.getZ(), maxDistance * maxDistance, positions)
                : JavaComputeKernels.filterWithinRadius(source.getX(), source.getY(), source.getZ(), maxDistance * maxDistance, positions);

            List<T> result = new ArrayList<>(matches.length);
            for (int index : matches) {
                result.add(filteredCandidates.get(index));
            }
            return result;
        }

        double[] distances = positions.length / 3 >= 32 && NativeBridge.isLoaded()
            ? NativeBridge.computeSquaredDistances(source.getX(), source.getY(), source.getZ(), positions)
            : JavaComputeKernels.squaredDistances(source.getX(), source.getY(), source.getZ(), positions);

        List<T> result = new ArrayList<>(filteredCandidates.size());
        for (int index = 0; index < filteredCandidates.size(); index++) {
            T candidate = filteredCandidates.get(index);
            if (testDistance(source, candidate, conditions, distances[index])) {
                result.add(candidate);
            }
        }

        return result;
    }
}
