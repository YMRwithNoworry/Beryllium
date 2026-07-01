package alku.beryllium.mixin;

import alku.beryllium.compute.NearestEntitySearch;
import alku.beryllium.compute.TargetingConditionsBatch;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@Mixin(EntityGetter.class)
public interface EntityGetterMixin {
    @Shadow
    List<? extends Player> players();

    @Shadow
    <T extends Entity> List<T> getEntitiesOfClass(Class<T> type, AABB box, Predicate<? super T> predicate);

    /**
     * @reason Batch nearest-player search through packed coordinates and native distance kernels.
     * @author YMRwithNoworry
     */
    @Overwrite
    @Nullable
    default Player getNearestPlayer(double x, double y, double z, double distance, @Nullable Predicate<Entity> predicate) {
        Predicate<Player> effectivePredicate = predicate == null ? player -> true : player -> predicate.test(player);
        return NearestEntitySearch.findNearestWithinExclusiveDistance(
            this.players(),
            effectivePredicate,
            distance < 0.0 ? -1.0 : distance * distance,
            x,
            y,
            z
        );
    }

    /**
     * @reason Delegate entity-origin nearest-player queries to the batched overload.
     * @author YMRwithNoworry
     */
    @Overwrite
    @Nullable
    default Player getNearestPlayer(Entity entity, double distance) {
        return this.getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), distance, false);
    }

    /**
     * @reason Preserve vanilla creative/spectator filtering while using the batched nearest-player path.
     * @author YMRwithNoworry
     */
    @Overwrite
    @Nullable
    default Player getNearestPlayer(double x, double y, double z, double distance, boolean ignoreCreative) {
        Predicate<Entity> predicate = ignoreCreative ? EntitySelector.NO_CREATIVE_OR_SPECTATOR : EntitySelector.NO_SPECTATORS;
        return this.getNearestPlayer(x, y, z, distance, predicate);
    }

    /**
     * @reason Use the same packed nearest-player path for presence checks.
     * @author YMRwithNoworry
     */
    @Overwrite
    default boolean hasNearbyAlivePlayer(double x, double y, double z, double distance) {
        return NearestEntitySearch.findNearestWithinExclusiveDistance(
            this.players(),
            player -> EntitySelector.NO_SPECTATORS.test(player) && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player),
            distance < 0.0 ? -1.0 : distance * distance,
            x,
            y,
            z
        ) != null;
    }

    /**
     * @reason Share the batched TargetingConditions evaluator for nearby-player lookups.
     * @author YMRwithNoworry
     */
    @Overwrite
    @Nullable
    default Player getNearestPlayer(TargetingConditions conditions, LivingEntity source) {
        return NearestEntitySearch.findNearest(this.players(), conditions, source, source.getX(), source.getY(), source.getZ());
    }

    /**
     * @reason Share the batched TargetingConditions evaluator for nearby-player lookups.
     * @author YMRwithNoworry
     */
    @Overwrite
    @Nullable
    default Player getNearestPlayer(TargetingConditions conditions, LivingEntity source, double x, double y, double z) {
        return NearestEntitySearch.findNearest(this.players(), conditions, source, x, y, z);
    }

    /**
     * @reason Share the batched TargetingConditions evaluator for nearby-player lookups.
     * @author YMRwithNoworry
     */
    @Overwrite
    @Nullable
    default Player getNearestPlayer(TargetingConditions conditions, double x, double y, double z) {
        return NearestEntitySearch.findNearest(this.players(), conditions, null, x, y, z);
    }

    /**
     * @reason Batch the class-filtered nearest-entity path before running distance checks.
     * @author YMRwithNoworry
     */
    @Overwrite
    @Nullable
    default <T extends LivingEntity> T getNearestEntity(
        Class<? extends T> type,
        TargetingConditions conditions,
        @Nullable LivingEntity source,
        double x,
        double y,
        double z,
        AABB box
    ) {
        return this.getNearestEntity(this.getEntitiesOfClass(type, box, candidate -> true), conditions, source, x, y, z);
    }

    /**
     * @reason Batch the entity-list nearest-entity path before running distance checks.
     * @author YMRwithNoworry
     */
    @Overwrite
    @Nullable
    default <T extends LivingEntity> T getNearestEntity(
        List<? extends T> candidates,
        TargetingConditions conditions,
        @Nullable LivingEntity source,
        double x,
        double y,
        double z
    ) {
        return NearestEntitySearch.findNearest(candidates, conditions, source, x, y, z);
    }

    /**
     * @reason Batch nearby-player filtering through packed coordinates and native distance kernels.
     * @author YMRwithNoworry
     */
    @Overwrite
    default List<Player> getNearbyPlayers(TargetingConditions conditions, LivingEntity source, AABB box) {
        return TargetingConditionsBatch.filterNearbyInBox(this.players(), conditions, source, box);
    }

    /**
     * @reason Batch nearby-entity filtering through packed coordinates and native distance kernels.
     * @author YMRwithNoworry
     */
    @Overwrite
    default <T extends LivingEntity> List<T> getNearbyEntities(Class<T> type, TargetingConditions conditions, LivingEntity source, AABB box) {
        return TargetingConditionsBatch.filterNearby(
            this.getEntitiesOfClass(type, box, candidate -> true),
            conditions,
            source,
            candidate -> true
        );
    }

    /**
     * @reason Use the server-side UUID map when available, then preserve the vanilla list scan fallback.
     * @author YMRwithNoworry
     */
    @Overwrite
    @Nullable
    default Player getPlayerByUUID(UUID uuid) {
        if (this instanceof ServerLevel serverLevel) {
            Player player = serverLevel.getServer().getPlayerList().getPlayer(uuid);
            if (player != null && player.level() == serverLevel) {
                return player;
            }
        }

        for (Player player : this.players()) {
            if (uuid.equals(player.getUUID())) {
                return player;
            }
        }

        return null;
    }
}
