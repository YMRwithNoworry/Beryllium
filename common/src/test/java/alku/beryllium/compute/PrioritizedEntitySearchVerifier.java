package alku.beryllium.compute;

import java.util.ArrayList;
import java.util.List;

public final class PrioritizedEntitySearchVerifier {
    private PrioritizedEntitySearchVerifier() {
    }

    public static void verifyFindFirstWithFallbackPrefersFirstPass() {
        List<SimpleCandidate> candidates = List.of(
            new SimpleCandidate(0, true, false, true),
            new SimpleCandidate(1, true, true, false),
            new SimpleCandidate(2, true, true, true)
        );
        List<String> calls = new ArrayList<>();

        SimpleCandidate match = PrioritizedEntitySearch.findFirstWithFallback(
            candidates,
            candidate -> {
                calls.add("required:" + candidate.id);
                return candidate.required;
            },
            candidate -> {
                calls.add("preferred:" + candidate.id);
                return candidate.preferred;
            },
            candidate -> {
                calls.add("fallback:" + candidate.id);
                return candidate.fallback;
            }
        );

        assertEquals(1, match.id, "preferred-pass match");
        assertListEquals(
            List.of("required:0", "preferred:0", "required:1", "preferred:1"),
            calls,
            "preferred-pass predicate order"
        );
    }

    public static void verifyFindFirstWithFallbackRescansForFallback() {
        List<SimpleCandidate> candidates = List.of(
            new SimpleCandidate(0, true, false, false),
            new SimpleCandidate(1, true, false, true),
            new SimpleCandidate(2, true, true, true)
        );
        List<String> calls = new ArrayList<>();

        SimpleCandidate match = PrioritizedEntitySearch.findFirstWithFallback(
            candidates,
            candidate -> {
                calls.add("required:" + candidate.id);
                return candidate.required;
            },
            candidate -> {
                calls.add("preferred:" + candidate.id);
                return candidate.preferred && candidate.id == 99;
            },
            candidate -> {
                calls.add("fallback:" + candidate.id);
                return candidate.fallback;
            }
        );

        assertEquals(1, match.id, "fallback-pass match");
        assertListEquals(
            List.of(
                "required:0",
                "preferred:0",
                "required:1",
                "preferred:1",
                "required:2",
                "preferred:2",
                "required:0",
                "fallback:0",
                "required:1",
                "fallback:1"
            ),
            calls,
            "fallback-pass predicate order"
        );
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private static void assertListEquals(List<String> expected, List<String> actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private record SimpleCandidate(int id, boolean required, boolean preferred, boolean fallback) {
    }
}
