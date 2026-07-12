package alku.beryllium.compute;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Preserves ChunkMap's spectator predicate, short-circuiting, and encounter order for X/Z distance checks.
 */
public final class ChunkDistanceSearch {
    private ChunkDistanceSearch() {
    }

    public static <T> List<T> filterWithinExclusiveDistance(
        Iterable<? extends T> candidates,
        double originX,
        double originZ,
        double radiusSquared,
        Predicate<? super T> eligible,
        ToDoubleFunction<? super T> xGetter,
        ToDoubleFunction<? super T> zGetter
    ) {
        if (candidates == null || eligible == null || xGetter == null || zGetter == null) {
            throw new IllegalArgumentException("candidates and accessors must not be null");
        }
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        List<T> result = new ArrayList<>();
        for (T candidate : candidates) {
            if (!eligible.test(candidate)) {
                continue;
            }

            double dx = xGetter.applyAsDouble(candidate) - originX;
            double dz = zGetter.applyAsDouble(candidate) - originZ;
            if (dx * dx + dz * dz < radiusSquared) {
                result.add(candidate);
            }
        }

        return result;
    }

    public static <T> boolean anyWithinExclusiveDistance(
        Iterable<? extends T> candidates,
        double originX,
        double originZ,
        double radiusSquared,
        Predicate<? super T> eligible,
        ToDoubleFunction<? super T> xGetter,
        ToDoubleFunction<? super T> zGetter
    ) {
        if (candidates == null || eligible == null || xGetter == null || zGetter == null) {
            throw new IllegalArgumentException("candidates and accessors must not be null");
        }
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        for (T candidate : candidates) {
            if (!eligible.test(candidate)) {
                continue;
            }

            double dx = xGetter.applyAsDouble(candidate) - originX;
            double dz = zGetter.applyAsDouble(candidate) - originZ;
            if (dx * dx + dz * dz < radiusSquared) {
                return true;
            }
        }

        return false;
    }
}
