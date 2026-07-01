package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.mixin.TargetingConditionsAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared nearest-entity search helpers that can switch between Java and Rust batch distance checks.
 */
public final class NearestEntitySearch {
    private NearestEntitySearch() {
    }

    @FunctionalInterface
    public interface DistancePredicate<T> {
        boolean test(T candidate, double distanceSquared);
    }

    public static <T extends LivingEntity> T findNearest(
        List<? extends T> candidates,
        TargetingConditions conditions,
        LivingEntity source,
        double originX,
        double originY,
        double originZ
    ) {
        if (source == null || hasConstantVisibilityRange(conditions)) {
            return findNearestWithinDistance(
                candidates,
                candidate -> TargetingConditionsBatch.pretest(source, candidate, conditions),
                source == null ? -1.0 : constantMaxDistanceSquared(conditions),
                originX,
                originY,
                originZ
            );
        }

        return findNearest(
            candidates,
            candidate -> TargetingConditionsBatch.pretest(source, candidate, conditions),
            (candidate, distanceSquared) -> TargetingConditionsBatch.testDistance(source, candidate, conditions, distanceSquared),
            originX,
            originY,
            originZ
        );
    }

    public static <T extends LivingEntity> T findNearest(
        List<? extends T> candidates,
        Predicate<? super T> predicate,
        double originX,
        double originY,
        double originZ
    ) {
        return findNearestWithinDistance(candidates, predicate, -1.0, originX, originY, originZ);
    }

    public static <T extends LivingEntity> T findNearestWithinDistance(
        List<? extends T> candidates,
        Predicate<? super T> predicate,
        double maxDistanceSquared,
        double originX,
        double originY,
        double originZ
    ) {
        return findNearestWithinDistance(candidates, predicate, maxDistanceSquared, true, originX, originY, originZ);
    }

    public static <T extends LivingEntity> T findNearestWithinExclusiveDistance(
        List<? extends T> candidates,
        Predicate<? super T> predicate,
        double maxDistanceSquared,
        double originX,
        double originY,
        double originZ
    ) {
        return findNearestWithinDistance(candidates, predicate, maxDistanceSquared, false, originX, originY, originZ);
    }

    public static <T extends LivingEntity> boolean hasAnyWithinExclusiveDistance(
        List<? extends T> candidates,
        Predicate<? super T> predicate,
        double maxDistanceSquared,
        double originX,
        double originY,
        double originZ
    ) {
        if (candidates.isEmpty()) {
            return false;
        }

        if (!NativeBatching.shouldUseNativeEntityBatch(candidates.size())) {
            for (T candidate : candidates) {
                if (
                    predicate.test(candidate)
                        && (maxDistanceSquared < 0.0 || candidate.distanceToSqr(originX, originY, originZ) < maxDistanceSquared)
                ) {
                    return true;
                }
            }

            return false;
        }

        List<T> filteredCandidates = filterCandidates(candidates, predicate);
        if (filteredCandidates.isEmpty()) {
            return false;
        }
        if (maxDistanceSquared < 0.0) {
            return true;
        }

        double[] positions = EntityPacking.packPositions(filteredCandidates);
        return NativeBridge.hasAnyWithinRadiusExclusive(originX, originY, originZ, maxDistanceSquared, positions);
    }

    private static <T extends LivingEntity> T findNearestWithinDistance(
        List<? extends T> candidates,
        Predicate<? super T> predicate,
        double maxDistanceSquared,
        boolean includeMaxDistance,
        double originX,
        double originY,
        double originZ
    ) {
        if (candidates.isEmpty()) {
            return null;
        }

        List<T> filteredCandidates = filterCandidates(candidates, predicate);
        if (filteredCandidates.isEmpty()) {
            return null;
        }

        double[] positions = EntityPacking.packPositions(filteredCandidates);
        int nearestIndex = NativeBatching.shouldUseNativeEntityBatch(positions.length / 3)
            ? findNearestIndexNative(originX, originY, originZ, maxDistanceSquared, includeMaxDistance, positions)
            : findNearestIndexJava(originX, originY, originZ, maxDistanceSquared, includeMaxDistance, positions);

        return nearestIndex >= 0 ? filteredCandidates.get(nearestIndex) : null;
    }

    public static <T extends LivingEntity> T findNearest(
        List<? extends T> candidates,
        Predicate<? super T> predicate,
        DistancePredicate<? super T> distancePredicate,
        double originX,
        double originY,
        double originZ
    ) {
        if (candidates.isEmpty()) {
            return null;
        }

        List<T> filteredCandidates = filterCandidates(candidates, predicate);
        if (filteredCandidates.isEmpty()) {
            return null;
        }

        double[] positions = EntityPacking.packPositions(filteredCandidates);

        double[] distances = NativeBatching.shouldUseNativeEntityBatch(positions.length / 3)
            ? NativeBridge.computeSquaredDistances(originX, originY, originZ, positions)
            : JavaComputeKernels.squaredDistances(originX, originY, originZ, positions);

        T nearest = null;
        double nearestDistance = -1.0;
        for (int index = 0; index < filteredCandidates.size(); index++) {
            double candidateDistance = distances[index];
            T candidate = filteredCandidates.get(index);
            if (!distancePredicate.test(candidate, candidateDistance)) {
                continue;
            }
            if (nearestDistance == -1.0 || candidateDistance < nearestDistance) {
                nearestDistance = candidateDistance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    private static <T extends LivingEntity> List<T> filterCandidates(
        List<? extends T> candidates,
        Predicate<? super T> predicate
    ) {
        List<T> filteredCandidates = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            if (predicate.test(candidate)) {
                filteredCandidates.add(candidate);
            }
        }

        return filteredCandidates;
    }

    private static boolean hasConstantVisibilityRange(TargetingConditions conditions) {
        TargetingConditionsAccessor accessor = (TargetingConditionsAccessor) conditions;
        return accessor.beryllium$range() <= 0.0 || !accessor.beryllium$testInvisible();
    }

    private static double constantMaxDistanceSquared(TargetingConditions conditions) {
        TargetingConditionsAccessor accessor = (TargetingConditionsAccessor) conditions;
        double range = accessor.beryllium$range();
        if (range <= 0.0) {
            return -1.0;
        }

        double maxDistance = Math.max(range, 2.0);
        return maxDistance * maxDistance;
    }

    private static int findNearestIndexNative(
        double originX,
        double originY,
        double originZ,
        double maxDistanceSquared,
        boolean includeMaxDistance,
        double[] positions
    ) {
        return includeMaxDistance
            ? NativeBridge.findNearestIndex(originX, originY, originZ, maxDistanceSquared, positions)
            : NativeBridge.findNearestIndexExclusive(originX, originY, originZ, maxDistanceSquared, positions);
    }

    private static int findNearestIndexJava(
        double originX,
        double originY,
        double originZ,
        double maxDistanceSquared,
        boolean includeMaxDistance,
        double[] positions
    ) {
        return includeMaxDistance
            ? JavaComputeKernels.findNearestIndex(originX, originY, originZ, maxDistanceSquared, positions)
            : JavaComputeKernels.findNearestIndexExclusive(originX, originY, originZ, maxDistanceSquared, positions);
    }
}
