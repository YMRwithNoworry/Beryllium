package alku.beryllium.mixin;

import alku.beryllium.config.BerylliumConfig;
import alku.beryllium.worldgen.AsyncChunkGenerator;
import alku.beryllium.worldgen.ChunkGenerationTracker;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 拦截服务端区块缓存，集成异步区块生成
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private ChunkMap chunkMap;

    @Unique
    private int beryllium$tickCounter = 0;

    @Unique
    private static final int PREFETCH_INTERVAL_TICKS = 20;

    /**
     * 定期更新玩家位置并触发预取
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void beryllium$onTick(CallbackInfo ci) {
        if (!BerylliumConfig.isAsyncChunkGenEnabled()) {
            return;
        }

        beryllium$tickCounter++;
        if (beryllium$tickCounter >= PREFETCH_INTERVAL_TICKS) {
            beryllium$tickCounter = 0;
            beryllium$updatePlayerPrefetch();
        }
    }

    /**
     * 更新所有玩家的预取
     */
    @Unique
    private void beryllium$updatePlayerPrefetch() {
        AsyncChunkGenerator asyncGen = AsyncChunkGenerator.getInstance();
        List<ServerPlayer> players = level.players();

        for (ServerPlayer player : players) {
            int renderDistance = player.requestedViewDistance();
            asyncGen.updatePlayerAndPrefetch(player, renderDistance);
        }
    }

    /**
     * 区块缓存关闭时，清理追踪器
     */
    @Inject(method = "close", at = @At("HEAD"))
    private void beryllium$onClose(CallbackInfo ci) {
        ChunkGenerationTracker.getInstance().clear();
    }
}
