package alku.beryllium.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Verifies reusable FFM sessions keep native calls isolated without changing outputs. */
public final class FfmNativeBridgeVerifier {
    private FfmNativeBridgeVerifier() {
    }

    public static void main(String[] args) throws Exception {
        NativeStatus status = NativeBridge.initialize();
        if (status != NativeStatus.OK) {
            throw new AssertionError("Expected native backend, got " + status);
        }

        verifyReuseAndOutputTail();
        verifyThreadIsolation();
    }

    private static void verifyReuseAndOutputTail() {
        int[] positions = {0, 0, 0, 2, 0, 0};
        int[] output = {77, 88, 99};
        long sessionId = FfmNativeBridge.sessionIdForCurrentThread();
        for (int iteration = 0; iteration < 8; iteration++) {
            int count = FfmNativeBridge.filterWithinRadius(
                0,
                0,
                0,
                1L,
                positions,
                output
            );
            assertEquals(1, count, "FFM filter count");
            assertArrayEquals(new int[] {0, 88, 99}, output, "FFM output tail");
        }

        assertEquals(
            sessionId,
            FfmNativeBridge.sessionIdForCurrentThread(),
            "FFM session reuse on one thread"
        );
    }

    private static void verifyThreadIsolation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Long>> sessions = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                sessions.add(executor.submit(() -> {
                    double[] positions = {0.0, 0.0, 0.0, 3.0, 0.0, 0.0};
                    int[] output = {-1, -1};
                    int count = FfmNativeBridge.filterWithinRadius(
                        0.0,
                        0.0,
                        0.0,
                        9.0,
                        positions,
                        output
                    );
                    assertEquals(2, count, "FFM concurrent filter count");
                    assertArrayEquals(new int[] {0, 1}, output, "FFM concurrent filter output");
                    return FfmNativeBridge.sessionIdForCurrentThread();
                }));
            }

            for (Future<Long> session : sessions) {
                if (session.get() <= 0L) {
                    throw new AssertionError("FFM worker session id must be positive");
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertArrayEquals(int[] expected, int[] actual, String message) {
        if (!java.util.Arrays.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + java.util.Arrays.toString(expected)
                + " but got " + java.util.Arrays.toString(actual));
        }
    }
}
