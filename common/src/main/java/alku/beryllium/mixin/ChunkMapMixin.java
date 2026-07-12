package alku.beryllium.mixin;

import alku.beryllium.compute.ChunkDistanceSearch;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {
    private static final double BERYLLIUM_SPAWN_RADIUS_SQUARED = 16384.0;

    /**
     * @reason Keep the distance-manager gate and preserve ordered X/Z filtering with the vanilla short circuit.
     * @author YMRwithNoworry
     */
    @Overwrite
    boolean anyPlayerCloseEnoughForSpawning(ChunkPos chunkPos) {
        if (!this.beryllium$hasPlayersNearby(chunkPos)) {
            return false;
        }

        double originX = SectionPos.sectionToBlockCoord(chunkPos.x, 8);
        double originZ = SectionPos.sectionToBlockCoord(chunkPos.z, 8);
        return ChunkDistanceSearch.anyWithinExclusiveDistance(
            ((ChunkMapAccessor) (Object) this).beryllium$playerMap().getAllPlayers(),
            originX,
            originZ,
            BERYLLIUM_SPAWN_RADIUS_SQUARED,
            player -> !player.isSpectator(),
            ServerPlayer::getX,
            ServerPlayer::getZ
        );
    }

    /**
     * @reason Keep vanilla immutable-result behavior while avoiding object-to-array conversion for a scalar check.
     * @author YMRwithNoworry
     */
    @Overwrite
    public List<ServerPlayer> getPlayersCloseForSpawning(ChunkPos chunkPos) {
        if (!this.beryllium$hasPlayersNearby(chunkPos)) {
            return List.of();
        }

        double originX = SectionPos.sectionToBlockCoord(chunkPos.x, 8);
        double originZ = SectionPos.sectionToBlockCoord(chunkPos.z, 8);
        return List.copyOf(ChunkDistanceSearch.filterWithinExclusiveDistance(
            ((ChunkMapAccessor) (Object) this).beryllium$playerMap().getAllPlayers(),
            originX,
            originZ,
            BERYLLIUM_SPAWN_RADIUS_SQUARED,
            player -> !player.isSpectator(),
            ServerPlayer::getX,
            ServerPlayer::getZ
        ));
    }

    /**
     * @reason Preserve the original spectator-first strict horizontal distance predicate.
     * @author YMRwithNoworry
     */
    @Overwrite
    private boolean playerIsCloseEnoughForSpawning(ServerPlayer player, ChunkPos chunkPos) {
        if (player.isSpectator()) {
            return false;
        }

        double originX = SectionPos.sectionToBlockCoord(chunkPos.x, 8);
        double originZ = SectionPos.sectionToBlockCoord(chunkPos.z, 8);
        double dx = originX - player.getX();
        double dz = originZ - player.getZ();
        return dx * dx + dz * dz < BERYLLIUM_SPAWN_RADIUS_SQUARED;
    }

    private boolean beryllium$hasPlayersNearby(ChunkPos chunkPos) {
        return ((ChunkMap) (Object) this).getDistanceManager().hasPlayersNearby(chunkPos.toLong());
    }
}
