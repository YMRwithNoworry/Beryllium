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
        if (source == null) {
            return findNearestWithinDistance(
                candidates,
                candidate -> TargetingConditionsBatch.pretest(source, candidate, conditions),
                -1.0,
                originX,
                originY,
                originZ
            );
        }

        TargetingConditionsAccessor accessor = (TargetingConditionsAccessor) conditions;
        if (accessor.beryllium$range() <= 0.0) {
            return findNearestWithinDistance(
                candidates,
                candidate -> TargetingConditionsBatch.pretest(source, candidate, conditions),
                -1.0,
                originX,
                originY,
                originZ
            );
        }

        List<T> filteredCandidates = filterCandidates(
            candidates,
            candidate -> TargetingConditionsBatch.pretestBeforeDistance(source, candidate, conditions)
        );
        if (filteredCandidates.isEmpty()) {
            return null;
        }

        double[] positions = EntityPacking.packPositions(filteredCandidates);
        double sourceX = source.getX();
        double sourceY = source.getY();
        double sourceZ = source.getZ();
        Predicate<? super T> posttest = candidate -> TargetingConditionsBatch.posttestAfterDistance(source, candidate, conditions);

        if (!accessor.beryllium$testInvisible()) {
            return findNearestAfterConstantDistanceAndPosttest(
                filteredCandidates,
                positions,
                sourceX,
                sourceY,
                sourceZ,
                originX,
                originY,
                originZ,
                constantMaxDistanceSquared(conditions),
                posttest
            );
        }

        double[] radiiSquared = accessor.beryllium$testInvisible()
            ? TargetingConditionsBatch.packVisibilityAdjustedRadii(filteredCandidates, source, accessor.beryllium$range())
            : null;

        if (!TargetingConditionsBatch.containsNaN(radiiSquared)) {
            return findNearestAfterVariableDistanceAndPosttest(
                filteredCandidates,
                positions,
                sourceX,
                sourceY,
                sourceZ,
                originX,
                originY,
                originZ,
                radiiSquared,
                posttest
            );
        }

        return findNearestAfterPrecomputedDistanceAndPosttest(
            filteredCandidates,
            positions,
            sourceX,
            sourceY,
            sourceZ,
            originX,
            originY,
            originZ,
            radiiSquared,
            posttest
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
        return hasAnyWithinExclusiveDistance(
            candidates,
            predicate,
            maxDistanceSquared,
            originX,
            originY,
            originZ,
            candidate -> candidate.getX(),
            candidate -> candidate.getY(),
            candidate -> candidate.getZ()
        );
    }

    static <T> boolean hasAnyWithinExclusiveDistance(
        List<? extends T> candidates,
        Predicate<? super T> predicate,
        double maxDistanceSquared,
        double originX,
        double originY,
        double originZ,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter
    ) {
        if (maxDistanceSquared < 0.0) {
            for (T candidate : candidates) {
                if (predicate.test(candidate)) {
                    return true;
                }
            }

            return false;
        }

        if (NativeBatching.shouldUseNativeEntityBatch(candidates.size())) {
            List<T> filteredCandidates = new ArrayList<>(candidates.size());
            for (T candidate : candidates) {
                if (predicate.test(candidate)) {
                    filteredCandidates.add(candidate);
                }
            }
            if (filteredCandidates.isEmpty()) {
                return false;
            }

            double[] positions = EntityPacking.packPositions(filteredCandidates, xGetter, yGetter, zGetter);
            return NativeBridge.hasAnyWithinRadiusExclusive(originX, originY, originZ, maxDistanceSquared, positions);
        }

        for (T candidate : candidates) {
            if (
                predicate.test(candidate)
                    && squaredDistance(originX, originY, originZ, xGetter, yGetter, zGetter, candidate) < maxDistanceSquared
            ) {
                return true;
            }
        }

        return false;
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

    static <T> T findNearestAfterConstantDistanceAndPosttest(
        List<T> filteredCandidates,
        double[] positions,
        double originX,
        double originY,
        double originZ,
        double maxDistanceSquared,
        Predicate<? super T> posttest
    ) {
        return findNearestAfterConstantDistanceAndPosttest(
            filteredCandidates,
            positions,
            originX,
            originY,
            originZ,
            originX,
            originY,
            originZ,
            maxDistanceSquared,
            posttest
        );
    }

    static <T> T findNearestAfterConstantDistanceAndPosttest(
        List<T> filteredCandidates,
        double[] positions,
        double distanceOriginX,
        double distanceOriginY,
        double distanceOriginZ,
        double nearestOriginX,
        double nearestOriginY,
        double nearestOriginZ,
        double maxDistanceSquared,
        Predicate<? super T> posttest
    ) {
        int[] matches = NativeBatching.shouldUseNativeEntityBatch(positions.length / 3)
            ? NativeBridge.filterWithinRadius(distanceOriginX, distanceOriginY, distanceOriginZ, maxDistanceSquared, positions)
            : JavaComputeKernels.filterWithinRadius(distanceOriginX, distanceOriginY, distanceOriginZ, maxDistanceSquared, positions);

        return findNearestFromMatchingIndices(filteredCandidates, positions, nearestOriginX, nearestOriginY, nearestOriginZ, matches, posttest);
    }

    static <T> T findNearestAfterVariableDistanceAndPosttest(
        List<T> filteredCandidates,
        double[] positions,
        double originX,
        double originY,
        double originZ,
        double[] radiiSquared,
        Predicate<? super T> posttest
    ) {
        return findNearestAfterVariableDistanceAndPosttest(
            filteredCandidates,
            positions,
            originX,
            originY,
            originZ,
            originX,
            originY,
            originZ,
            radiiSquared,
            posttest
        );
    }

    static <T> T findNearestAfterVariableDistanceAndPosttest(
        List<T> filteredCandidates,
        double[] positions,
        double distanceOriginX,
        double distanceOriginY,
        double distanceOriginZ,
        double nearestOriginX,
        double nearestOriginY,
        double nearestOriginZ,
        double[] radiiSquared,
        Predicate<? super T> posttest
    ) {
        int[] matches = TargetingConditionsBatch.filterIndicesByVariableDistance(
            positions,
            distanceOriginX,
            distanceOriginY,
            distanceOriginZ,
            radiiSquared
        );

        return findNearestFromMatchingIndices(filteredCandidates, positions, nearestOriginX, nearestOriginY, nearestOriginZ, matches, posttest);
    }

    static <T> T findNearestAfterPrecomputedDistanceAndPosttest(
        List<T> filteredCandidates,
        double[] positions,
        double originX,
        double originY,
        double originZ,
        double[] radiiSquared,
        Predicate<? super T> posttest
    ) {
        return findNearestAfterPrecomputedDistanceAndPosttest(
            filteredCandidates,
            positions,
            originX,
            originY,
            originZ,
            originX,
            originY,
            originZ,
            radiiSquared,
            posttest
        );
    }

    static <T> T findNearestAfterPrecomputedDistanceAndPosttest(
        List<T> filteredCandidates,
        double[] positions,
        double distanceOriginX,
        double distanceOriginY,
        double distanceOriginZ,
        double nearestOriginX,
        double nearestOriginY,
        double nearestOriginZ,
        double[] radiiSquared,
        Predicate<? super T> posttest
    ) {
        double[] distanceGateDistances = NativeBatching.shouldUseNativeEntityBatch(positions.length / 3)
            ? NativeBridge.computeSquaredDistances(distanceOriginX, distanceOriginY, distanceOriginZ, positions)
            : JavaComputeKernels.squaredDistances(distanceOriginX, distanceOriginY, distanceOriginZ, positions);

        T nearest = null;
        double nearestDistance = -1.0;
        for (int index = 0; index < filteredCandidates.size(); index++) {
            if (!TargetingConditionsBatch.withinPrecomputedRadius(distanceGateDistances[index], radiiSquared[index])) {
                continue;
            }
            T candidate = filteredCandidates.get(index);
            if (!posttest.test(candidate)) {
                continue;
            }
            double candidateDistance = squaredDistanceAt(nearestOriginX, nearestOriginY, nearestOriginZ, positions, index);
            if (nearestDistance == -1.0 || candidateDistance < nearestDistance) {
                nearestDistance = candidateDistance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    private static <T> T findNearestFromMatchingIndices(
        List<T> filteredCandidates,
        double[] positions,
        double originX,
        double originY,
        double originZ,
        int[] matches,
        Predicate<? super T> posttest
    ) {
        T nearest = null;
        double nearestDistance = -1.0;
        for (int index : matches) {
            T candidate = filteredCandidates.get(index);
            if (!posttest.test(candidate)) {
                continue;
            }

            double candidateDistance = squaredDistanceAt(originX, originY, originZ, positions, index);
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

    private static double squaredDistanceAt(double originX, double originY, double originZ, double[] positions, int index) {
        int offset = index * 3;
        double dx = positions[offset] - originX;
        double dy = positions[offset + 1] - originY;
        double dz = positions[offset + 2] - originZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static <T> double squaredDistance(
        double originX,
        double originY,
        double originZ,
        EntityPacking.CoordinateGetter<? super T> xGetter,
        EntityPacking.CoordinateGetter<? super T> yGetter,
        EntityPacking.CoordinateGetter<? super T> zGetter,
        T value
    ) {
        double dx = xGetter.get(value) - originX;
        double dy = yGetter.get(value) - originY;
        double dz = zGetter.get(value) - originZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
