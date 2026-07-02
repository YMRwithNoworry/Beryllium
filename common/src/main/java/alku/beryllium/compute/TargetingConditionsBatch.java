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
        return pretestBeforeDistance(source, target, conditions) && posttestAfterDistance(source, target, conditions);
    }

    static boolean pretestBeforeDistance(@Nullable LivingEntity source, LivingEntity target, TargetingConditions conditions) {
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

        return true;
    }

    static boolean posttestAfterDistance(@Nullable LivingEntity source, LivingEntity target, TargetingConditions conditions) {
        if (!requiresPostDistanceLineOfSight(source, conditions)) {
            return true;
        }

        if (!((Mob) source).getSensing().hasLineOfSight(target)) {
            return false;
        }

        return true;
    }

    static boolean requiresPostDistanceLineOfSight(@Nullable LivingEntity source, TargetingConditions conditions) {
        if (!(source instanceof Mob)) {
            return false;
        }

        TargetingConditionsAccessor accessor = (TargetingConditionsAccessor) conditions;
        return accessor.beryllium$checkLineOfSight();
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
            List<T> filteredCandidates = new ArrayList<>(candidates.size());
            for (T candidate : candidates) {
                if (candidatePredicate.test(candidate) && pretest(null, candidate, conditions)) {
                    filteredCandidates.add(candidate);
                }
            }

            return filteredCandidates.isEmpty() ? List.of() : List.copyOf(filteredCandidates);
        }

        List<T> filteredCandidates = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            if (candidatePredicate.test(candidate) && pretestBeforeDistance(source, candidate, conditions)) {
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
        List<T> boxedCandidates = filterCandidatesWithinAabb(
            candidates,
            candidate -> candidate.getX(),
            candidate -> candidate.getY(),
            candidate -> candidate.getZ(),
            box
        );

        if (boxedCandidates.isEmpty()) {
            return List.of();
        }

        if (source == null) {
            List<T> filteredCandidates = new ArrayList<>(boxedCandidates.size());
            for (T candidate : boxedCandidates) {
                if (pretest(null, candidate, conditions)) {
                    filteredCandidates.add(candidate);
                }
            }

            return filteredCandidates.isEmpty() ? List.of() : List.copyOf(filteredCandidates);
        }

        List<T> filteredCandidates = new ArrayList<>(boxedCandidates.size());
        for (T candidate : boxedCandidates) {
            if (pretestBeforeDistance(source, candidate, conditions)) {
                filteredCandidates.add(candidate);
            }
        }

        if (filteredCandidates.isEmpty()) {
            return List.of();
        }

        return filterByDistance(filteredCandidates, conditions, source);
    }

    static <T> List<T> filterCandidatesWithinAabb(
        List<? extends T> candidates,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter,
        AABB box
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        double[] positions = EntityPacking.packPositions(candidates, xGetter, yGetter, zGetter);
        int[] boxMatches = NativeBatching.shouldUseNativeEntityBatch(positions.length / 3)
            ? NativeBridge.filterWithinAabb(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, positions)
            : JavaComputeKernels.filterWithinAabb(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, positions);

        if (boxMatches.length == 0) {
            return List.of();
        }

        List<T> boxedCandidates = new ArrayList<>(boxMatches.length);
        for (int index : boxMatches) {
            boxedCandidates.add(candidates.get(index));
        }

        return boxedCandidates;
    }

    static <T> List<T> filterByConstantDistanceAndPosttest(
        List<T> filteredCandidates,
        double[] positions,
        double originX,
        double originY,
        double originZ,
        double maxDistanceSquared,
        Predicate<? super T> posttest
    ) {
        int[] matches = NativeBatching.shouldUseNativeEntityBatch(positions.length / 3)
            ? NativeBridge.filterWithinRadius(originX, originY, originZ, maxDistanceSquared, positions)
            : JavaComputeKernels.filterWithinRadius(originX, originY, originZ, maxDistanceSquared, positions);

        List<T> result = new ArrayList<>(matches.length);
        for (int index : matches) {
            T candidate = filteredCandidates.get(index);
            if (posttest.test(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    static <T> List<T> filterByVariableDistanceAndPosttest(
        List<T> filteredCandidates,
        double[] positions,
        double originX,
        double originY,
        double originZ,
        double[] radiiSquared,
        Predicate<? super T> posttest
    ) {
        int[] matches = NativeBatching.shouldUseNativeEntityBatch(positions.length / 3)
            ? NativeBridge.filterWithinRadii(originX, originY, originZ, positions, radiiSquared)
            : JavaComputeKernels.filterWithinRadii(originX, originY, originZ, positions, radiiSquared);

        List<T> result = new ArrayList<>(matches.length);
        for (int index : matches) {
            T candidate = filteredCandidates.get(index);
            if (posttest.test(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    static <T> int[] filterIndicesByVariableDistance(
        double[] positions,
        double originX,
        double originY,
        double originZ,
        double[] radiiSquared
    ) {
        return NativeBatching.shouldUseNativeEntityBatch(positions.length / 3)
            ? NativeBridge.filterWithinRadii(originX, originY, originZ, positions, radiiSquared)
            : JavaComputeKernels.filterWithinRadii(originX, originY, originZ, positions, radiiSquared);
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
            if (!requiresPostDistanceLineOfSight(source, conditions)) {
                return new ArrayList<>(filteredCandidates);
            }

            List<T> result = new ArrayList<>(filteredCandidates.size());
            for (T candidate : filteredCandidates) {
                if (posttestAfterDistance(source, candidate, conditions)) {
                    result.add(candidate);
                }
            }
            return result;
        }

        if (!accessor.beryllium$testInvisible()) {
            double maxDistance = Math.max(range, 2.0);
            return filterByConstantDistanceAndPosttest(
                filteredCandidates,
                positions,
                source.getX(),
                source.getY(),
                source.getZ(),
                maxDistance * maxDistance,
                candidate -> posttestAfterDistance(source, candidate, conditions)
            );
        }

        double[] radiiSquared = packVisibilityAdjustedRadii(filteredCandidates, source, range);
        if (!containsNaN(radiiSquared)) {
            return filterByVariableDistanceAndPosttest(
                filteredCandidates,
                positions,
                source.getX(),
                source.getY(),
                source.getZ(),
                radiiSquared,
                candidate -> posttestAfterDistance(source, candidate, conditions)
            );
        }

        double[] distances = NativeBatching.shouldUseNativeEntityBatch(positions.length / 3)
            ? NativeBridge.computeSquaredDistances(source.getX(), source.getY(), source.getZ(), positions)
            : JavaComputeKernels.squaredDistances(source.getX(), source.getY(), source.getZ(), positions);
        List<T> result = new ArrayList<>(filteredCandidates.size());
        for (int index = 0; index < filteredCandidates.size(); index++) {
            T candidate = filteredCandidates.get(index);
            if (
                withinPrecomputedRadius(distances[index], radiiSquared[index])
                    && posttestAfterDistance(source, candidate, conditions)
            ) {
                result.add(candidate);
            }
        }

        return result;
    }

    static <T extends LivingEntity> double[] packVisibilityAdjustedRadii(List<T> candidates, LivingEntity source, double range) {
        double[] radiiSquared = new double[candidates.size()];
        for (int index = 0; index < candidates.size(); index++) {
            double maxDistance = Math.max(range * candidates.get(index).getVisibilityPercent(source), 2.0);
            radiiSquared[index] = maxDistance * maxDistance;
        }
        return radiiSquared;
    }

    static boolean containsNaN(double[] values) {
        for (double value : values) {
            if (Double.isNaN(value)) {
                return true;
            }
        }
        return false;
    }

    static boolean withinPrecomputedRadius(double distanceSquared, double radiusSquared) {
        return Double.isNaN(radiusSquared) || distanceSquared <= radiusSquared;
    }
}
