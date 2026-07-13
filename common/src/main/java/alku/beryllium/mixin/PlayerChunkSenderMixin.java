package alku.beryllium.mixin;

import alku.beryllium.compute.ChunkSendBatchSelector;
import alku.beryllium.compute.NativeBatching;
import com.google.common.collect.Comparators;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Mixin(PlayerChunkSender.class)
public class PlayerChunkSenderMixin {
    @Shadow
    @Final
    private LongSet pendingChunks;

    @Shadow
    @Final
    private boolean memoryConnection;

    @Shadow
    private float batchQuota;

    /**
     * @reason Preserve both vanilla branches while replacing boxed partial Top-K selection with a primitive FFM batch.
     * @author YMRwithNoworry
     */
    @Overwrite
    private List<LevelChunk> collectChunksToSend(ChunkMap chunkMap, ChunkPos chunkPos) {
        int limit = Mth.floor(this.batchQuota);
        List<LevelChunk> chunks;
        if (!this.memoryConnection
            && this.pendingChunks.size() > limit
            && NativeBatching.shouldUseNativeChunkSendSelection(this.pendingChunks.size())) {
            long[] packedChunkPositions = this.pendingChunks.longStream().toArray();
            int[] selectedIndices = ChunkSendBatchSelector.selectNearestChunkIndices(
                chunkPos.x,
                chunkPos.z,
                packedChunkPositions,
                limit
            );
            List<LevelChunk> selectedChunks = new ArrayList<>(selectedIndices.length);
            for (int selectedIndex : selectedIndices) {
                LevelChunk chunk = chunkMap.getChunkToSend(packedChunkPositions[selectedIndex]);
                if (chunk != null) {
                    selectedChunks.add(chunk);
                }
            }
            chunks = selectedChunks.stream().toList();
        } else if (!this.memoryConnection && this.pendingChunks.size() > limit) {
            chunks = this.pendingChunks
                .stream()
                .collect(Comparators.least(limit, Comparator.comparingInt(chunkPos::distanceSquared)))
                .stream()
                .mapToLong(Long::longValue)
                .mapToObj(chunkMap::getChunkToSend)
                .filter(Objects::nonNull)
                .toList();
        } else {
            chunks = this.pendingChunks
                .longStream()
                .mapToObj(chunkMap::getChunkToSend)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(chunk -> chunkPos.distanceSquared(chunk.getPos())))
                .toList();
        }

        for (LevelChunk chunk : chunks) {
            this.pendingChunks.remove(chunk.getPos().toLong());
        }
        return chunks;
    }
}
