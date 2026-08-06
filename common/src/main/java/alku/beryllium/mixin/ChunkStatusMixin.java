package alku.beryllium.mixin;

import alku.beryllium.config.BerylliumConfig;
import alku.beryllium.worldgen.AsyncChunkGenerator;
import alku.beryllium.worldgen.ChunkGenerationTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 拦截区块状态生成，在 NOISE/SURFACE 阶段使用异步计算
 */
@Mixin(ChunkStatus.class)
public abstract class ChunkStatusMixin {

    /**
     * 拦截区块生成步骤，判断是否使用异步生成
     */
    @Inject(
        method = "generate",
        at = @At("HEAD"),
        cancellable = true
    )
    private void beryllium$interceptGeneration(
        ServerLevel level,
        net.minecraft.world.level.chunk.ChunkGenerator generator,
        List<ChunkAccess> chunks,
        ChunkAccess centerChunk,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir
    ) {
        if (!BerylliumConfig.isAsyncChunkGenEnabled()) {
            return;
        }

        ChunkStatus currentStatus = (ChunkStatus) (Object) this;

        // 只在 NOISE 和 SURFACE 阶段使用异步生成
        if (!beryllium$shouldAsyncGenerate(currentStatus)) {
            return;
        }

        ChunkPos chunkPos = centerChunk.getPos();

        // 检查是否已经在生成中
        ChunkGenerationTracker tracker = ChunkGenerationTracker.getInstance();
        if (tracker.isGenerating(chunkPos)) {
            return; // 避免重复提交
        }

        // 查找最近的玩家
        ChunkPos nearestPlayerPos = beryllium$findNearestPlayer(level, chunkPos);
        if (nearestPlayerPos == null) {
            return; // 没有玩家，使用默认逻辑
        }

        // 判断是否应该异步生成
        AsyncChunkGenerator asyncGen = AsyncChunkGenerator.getInstance();
        if (!asyncGen.shouldGenerateAsync(nearestPlayerPos, chunkPos)) {
            return; // 距离太近，使用同步生成
        }

        // 标记为正在生成
        tracker.markGenerating(chunkPos);

        // 异步生成区块
        CompletableFuture<ChunkAccess> asyncFuture = asyncGen.submitAsync(
            level,
            chunkPos,
            nearestPlayerPos,
            () -> {
                // 这里会调用原始的生成逻辑
                // 通过不取消来让原始方法继续执行
                return centerChunk;
            }
        );

        // 完成后清理追踪
        asyncFuture.whenComplete((chunk, throwable) -> {
            tracker.markCompleted(chunkPos);
        });

        // 不取消，让原始方法继续执行
        // 异步任务只是为了预加载和缓存
    }

    /**
     * 判断当前状态是否应该使用异步生成
     */
    @Unique
    private boolean beryllium$shouldAsyncGenerate(ChunkStatus status) {
        // 只在计算密集型阶段使用异步生成
        return status == ChunkStatus.NOISE || status == ChunkStatus.SURFACE;
    }

    /**
     * 查找最近的玩家位置
     */
    @Unique
    private ChunkPos beryllium$findNearestPlayer(ServerLevel level, ChunkPos chunkPos) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return null;
        }

        ServerPlayer nearestPlayer = null;
        int minDistanceSquared = Integer.MAX_VALUE;

        for (ServerPlayer player : players) {
            ChunkPos playerPos = player.chunkPosition();
            int distanceSquared = chunkPos.distanceSquared(playerPos);

            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                nearestPlayer = player;
            }
        }

        return nearestPlayer != null ? nearestPlayer.chunkPosition() : null;
    }
}
