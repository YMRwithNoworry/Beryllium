package alku.beryllium.compute;

import java.util.List;
import java.util.function.Predicate;

/**
 * Ordered two-pass search for vanilla paths that use Optional.or fallback semantics.
 */
public final class PrioritizedEntitySearch {
    private PrioritizedEntitySearch() {
    }

    public static <T> T findFirstWithFallback(
        List<? extends T> candidates,
        Predicate<? super T> requiredPredicate,
        Predicate<? super T> preferredPredicate,
        Predicate<? super T> fallbackPredicate
    ) {
        T preferred = findFirst(candidates, requiredPredicate, preferredPredicate);
        return preferred != null ? preferred : findFirst(candidates, requiredPredicate, fallbackPredicate);
    }

    private static <T> T findFirst(
        List<? extends T> candidates,
        Predicate<? super T> requiredPredicate,
        Predicate<? super T> groupPredicate
    ) {
        for (T candidate : candidates) {
            if (requiredPredicate.test(candidate) && groupPredicate.test(candidate)) {
                return candidate;
            }
        }

        return null;
    }
}
