package alku.beryllium.mixin;

import alku.beryllium.compute.EntityDistanceSort;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.PlayerSensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(PlayerSensor.class)
public class PlayerSensorMixin {
    /**
     * @reason Replace player sensor streams with allocation-aware loops while retaining native sorting for large lists.
     * @author YMRwithNoworry
     */
    @Overwrite
    protected void doTick(ServerLevel level, LivingEntity entity) {
        List<Player> nearbyPlayers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (EntitySelector.NO_SPECTATORS.test(player) && entity.closerThan(player, 16.0)) {
                nearbyPlayers.add(player);
            }
        }
        EntityDistanceSort.sortByDistance(nearbyPlayers, entity);

        Brain<?> brain = entity.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_PLAYERS, nearbyPlayers);

        List<Player> visiblePlayers = new ArrayList<>();
        for (Player player : nearbyPlayers) {
            if (Sensor.isEntityTargetable(entity, player)) {
                visiblePlayers.add(player);
            }
        }

        brain.setMemory(
            MemoryModuleType.NEAREST_VISIBLE_PLAYER,
            visiblePlayers.isEmpty() ? null : visiblePlayers.getFirst()
        );

        Optional<Player> attackablePlayer = Optional.empty();
        for (Player player : visiblePlayers) {
            if (Sensor.isEntityAttackable(entity, player)) {
                attackablePlayer = Optional.of(player);
                break;
            }
        }
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, attackablePlayer);
    }
}
