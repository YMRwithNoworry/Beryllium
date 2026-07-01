package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared nearest-entity search helpers that can switch between Java and Rust batch distance checks.
 */
public final class NearestEntitySearch {
    private static final int NATIVE_BATCH_THRESHOLD = 32;

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
        if (candidates.isEmpty()) {
            return null;
        }

        List<T> filteredCandidates = filterCandidates(candidates, predicate);
        if (filteredCandidates.isEmpty()) {
            return null;
        }

        double[] positions = EntityPacking.packPositions(filteredCandidates);
        int nearestIndex = positions.length / 3 >= NATIVE_BATCH_THRESHOLD && NativeBridge.isLoaded()
            ? NativeBridge.findNearestIndex(originX, originY, originZ, maxDistanceSquared, positions)
            : JavaComputeKernels.findNearestIndex(originX, originY, originZ, maxDistanceSquared, positions);

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

        double[] distances = positions.length / 3 >= NATIVE_BATCH_THRESHOLD && NativeBridge.isLoaded()
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
}
