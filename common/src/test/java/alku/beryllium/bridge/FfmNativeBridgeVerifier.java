package alku.beryllium.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;

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
        verifyUninitializedOutputsPreserveJavaTails();
        verifyCubeclSidecarCannotDisableMainBackend();
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

    private static void verifyUninitializedOutputsPreserveJavaTails() {
        long[] chunkPositions = {packChunk(2, 0), packChunk(0, 1), packChunk(-3, 0)};
        int[] selectedChunks = {71, 72, 73, 74};
        int selectedChunkCount = FfmNativeBridge.selectNearestChunkIndices(
            0,
            0,
            chunkPositions,
            2,
            selectedChunks
        );
        assertEquals(2, selectedChunkCount, "FFM exact chunk prefix count");
        assertArrayEquals(new int[] {1, 0, 73, 74}, selectedChunks, "FFM exact chunk prefix tail");

        double[] entityPositions = {0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 4.0, 0.0, 0.0};
        int[] nearest = {81, 82, 83};
        int nearestCount = FfmNativeBridge.selectNearestIndicesWithinRadiusExclusive(
            0.0,
            0.0,
            0.0,
            1.0,
            entityPositions,
            2,
            nearest
        );
        assertEquals(1, nearestCount, "FFM exact nearest prefix count");
        assertArrayEquals(new int[] {0, 82, 83}, nearest, "FFM exact nearest prefix tail");

        double[] potentialOutput = {91.0, 92.0};
        int potentialStatus = FfmNativeBridge.computePotentialEnergyChange(
            0,
            0,
            0,
            new int[] {1, 0, 0},
            new double[] {2.0},
            1.0,
            potentialOutput
        );
        assertEquals(NativeStatus.OK.code(), potentialStatus, "FFM exact potential status");
        assertDoubleArrayEquals(new double[] {2.0, 92.0}, potentialOutput, "FFM exact potential tail");

        int[] undersizedOutput = {101};
        int rejected = FfmNativeBridge.selectNearestChunkIndices(
            0,
            0,
            chunkPositions,
            chunkPositions.length,
            2,
            undersizedOutput,
            undersizedOutput.length
        );
        if (rejected >= 0) {
            throw new AssertionError("FFM undersized exact output should be rejected, got " + rejected);
        }
        assertArrayEquals(new int[] {101}, undersizedOutput, "FFM rejected exact output");
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

                    long[] chunkPositions = {packChunk(2, 0), packChunk(0, 1), packChunk(-3, 0)};
                    int[] selectedChunks = {-1, -1};
                    int selectedCount = FfmNativeBridge.selectNearestChunkIndices(
                        0,
                        0,
                        chunkPositions,
                        2,
                        selectedChunks
                    );
                    assertEquals(2, selectedCount, "FFM concurrent exact-call count");
                    assertArrayEquals(new int[] {1, 0}, selectedChunks, "FFM concurrent exact-call output");
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

    private static void verifyCubeclSidecarCannotDisableMainBackend() {
        if (!NativeLibraryLoader.hasCubeclPreviewCandidate()) {
            return;
        }

        boolean sidecarLoaded = NativeLibraryLoader.tryLoadCubeclPreview();
        if (!FfmNativeBridge.isAvailable()) {
            throw new AssertionError("CubeCL sidecar load attempt disabled the main native backend");
        }

        int count = 262_144;
        int[] positions = new int[count * 3];
        double[] charges = new double[count];
        for (int index = 0; index < count; index++) {
            int offset = index * 3;
            positions[offset] = index % 4_096 - 2_048;
            positions[offset + 1] = 65 + index % 31;
            positions[offset + 2] = index % 31 - 15;
            charges[index] = (index % 17 - 8) * 0.25;
        }

        double[] expected = {Double.NaN};
        assertEquals(
            NativeStatus.OK.code(),
            FfmNativeBridge.computePotentialEnergyChange(0, 64, 0, positions, charges, 0.75, expected),
            "FFM sidecar reference status"
        );
        assertEquals(
            NativeStatus.OK.code(),
            FfmNativeBridge.setPotentialCharges(positions, charges),
            "FFM sidecar cache status"
        );
        double[] actual = {Double.NaN};
        assertEquals(
            NativeStatus.OK.code(),
            FfmNativeBridge.computePotentialEnergyChangeCached(0, 64, 0, 0.75, actual),
            "FFM sidecar cached status"
        );
        if (Double.doubleToRawLongBits(expected[0]) != Double.doubleToRawLongBits(actual[0])) {
            throw new AssertionError(
                "FFM sidecar fallback changed result: expected=" + expected[0] + ", actual=" + actual[0]
            );
        }

        if (sidecarLoaded) {
            long deadline = System.nanoTime() + 5_000_000_000L;
            int previewStatus;
            do {
                previewStatus = FfmNativeBridge.cubeclPreviewStatus();
                if (previewStatus != 1) {
                    break;
                }
                LockSupport.parkNanos(10_000_000L);
            } while (System.nanoTime() < deadline);
            if (previewStatus == 1) {
                throw new AssertionError("CubeCL sidecar calibration did not finish within five seconds");
            }
            if (previewStatus == 2) {
                double[] cubeclActual = {Double.NaN};
                assertEquals(
                    NativeStatus.OK.code(),
                    FfmNativeBridge.computePotentialEnergyChangeCached(0, 64, 0, 0.75, cubeclActual),
                    "FFM CubeCL cached status"
                );
                if (Double.doubleToRawLongBits(expected[0]) != Double.doubleToRawLongBits(cubeclActual[0])) {
                    throw new AssertionError(
                        "FFM CubeCL result changed: expected=" + expected[0] + ", actual=" + cubeclActual[0]
                    );
                }
            }
            System.out.println("CubeCL preview status after calibration: " + previewStatus);
        }
    }

    private static long packChunk(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
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

    private static void assertDoubleArrayEquals(double[] expected, double[] actual, String message) {
        if (!java.util.Arrays.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + java.util.Arrays.toString(expected)
                + " but got " + java.util.Arrays.toString(actual));
        }
    }
}
