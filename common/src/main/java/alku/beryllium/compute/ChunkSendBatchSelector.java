package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects the distance-ordered subset used by PlayerChunkSender's partial batch path.
 */
public final class ChunkSendBatchSelector {
    private static final ThreadLocal<ScratchPool> SCRATCH_POOL = ThreadLocal.withInitial(ScratchPool::new);

    private ChunkSendBatchSelector() {
    }

    public static int[] selectNearestChunkIndices(
        int originX,
        int originZ,
        long[] packedChunkPositions,
        int limit
    ) {
        if (packedChunkPositions == null) {
            throw new IllegalArgumentException("packedChunkPositions must not be null");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }

        int[] output = new int[Math.min(limit, packedChunkPositions.length)];
        selectNearestChunkIndices(originX, originZ, packedChunkPositions, limit, output);
        return output;
    }

    public static int selectNearestChunkIndices(
        int originX,
        int originZ,
        long[] packedChunkPositions,
        int limit,
        int[] output
    ) {
        return selectNearestChunkIndices(
            originX,
            originZ,
            packedChunkPositions,
            packedChunkPositions == null ? 0 : packedChunkPositions.length,
            limit,
            output
        );
    }

    public static int selectNearestChunkIndices(
        int originX,
        int originZ,
        long[] packedChunkPositions,
        int candidateCount,
        int limit,
        int[] output
    ) {
        if (packedChunkPositions != null && NativeBatching.shouldUseNativeChunkSendSelection(candidateCount)) {
            return NativeBridge.selectNearestChunkIndices(
                originX,
                originZ,
                packedChunkPositions,
                candidateCount,
                limit,
                output
            );
        }
        return JavaComputeKernels.selectNearestChunkIndices(
            originX,
            originZ,
            packedChunkPositions,
            candidateCount,
            limit,
            output
        );
    }

    public static Scratch acquireScratch(int candidateCount, int limit) {
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount must be non-negative");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        return SCRATCH_POOL.get().acquire(candidateCount, Math.min(candidateCount, limit));
    }

    public static void releaseScratch(Scratch scratch) {
        SCRATCH_POOL.get().release(scratch);
    }

    public static final class Scratch {
        private long[] packedChunkPositions = new long[0];
        private int[] selectedIndices = new int[0];

        private Scratch() {
        }

        private void prepare(int candidateCount, int selectedCount) {
            if (this.packedChunkPositions.length < candidateCount) {
                this.packedChunkPositions = new long[candidateCount];
            }
            if (this.selectedIndices.length < selectedCount) {
                this.selectedIndices = new int[selectedCount];
            }
        }

        public int snapshot(long[] keyTable, boolean containsNull) {
            if (keyTable == null || keyTable.length == 0) {
                throw new IllegalArgumentException("keyTable must contain the FastUtil null-key slot");
            }
            int snapshotSize = 0;
            if (containsNull) {
                this.packedChunkPositions[snapshotSize++] = 0L;
            }
            int bucketCount = keyTable.length - 1;
            for (int bucket = 0; bucket < bucketCount; bucket++) {
                long packedChunkPosition = keyTable[bucket];
                if (packedChunkPosition != 0L) {
                    this.packedChunkPositions[snapshotSize++] = packedChunkPosition;
                }
            }
            return snapshotSize;
        }

        public long[] packedChunkPositions() {
            return this.packedChunkPositions;
        }

        public int[] selectedIndices() {
            return this.selectedIndices;
        }
    }

    private static final class ScratchPool {
        private final Scratch primary = new Scratch();
        private List<Scratch> nested;
        private int depth;

        private Scratch acquire(int candidateCount, int selectedCount) {
            Scratch scratch;
            if (this.depth == 0) {
                scratch = this.primary;
            } else {
                int nestedIndex = this.depth - 1;
                if (this.nested == null) {
                    this.nested = new ArrayList<>();
                }
                if (nestedIndex == this.nested.size()) {
                    this.nested.add(new Scratch());
                }
                scratch = this.nested.get(nestedIndex);
            }
            scratch.prepare(candidateCount, selectedCount);
            this.depth++;
            return scratch;
        }

        private void release(Scratch scratch) {
            if (this.depth <= 0) {
                throw new IllegalStateException("chunk send scratch must be released in reverse acquisition order");
            }
            Scratch expected = this.depth == 1 ? this.primary : this.nested.get(this.depth - 2);
            if (expected != scratch) {
                throw new IllegalStateException("chunk send scratch must be released in reverse acquisition order");
            }
            this.depth--;
        }
    }
}
