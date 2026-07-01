package alku.beryllium.mixin;

import alku.beryllium.compute.EntityDistanceSort;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.TemptingSensor;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(TemptingSensor.class)
public class TemptingSensorMixin {
    @Shadow
    @Final
    private static TargetingConditions TEMPT_TARGETING;

    @Shadow
    @Final
    private Predicate<ItemStack> temptations;

    /**
     * @reason Batch tempting-player distance ordering through the shared native sort path.
     * @author YMRwithNoworry
     */
    @Overwrite
    protected void doTick(ServerLevel level, PathfinderMob mob) {
        Brain<?> brain = mob.getBrain();
        List<ServerPlayer> temptingPlayers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (
                EntitySelector.NO_SPECTATORS.test(player)
                    && TEMPT_TARGETING.test(mob, player)
                    && mob.closerThan(player, 10.0)
                    && this.beryllium$playerHoldingTemptation(player)
                    && !mob.hasPassenger(player)
            ) {
                temptingPlayers.add(player);
            }
        }

        EntityDistanceSort.sortByDistance(temptingPlayers, mob);
        if (temptingPlayers.isEmpty()) {
            brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
        } else {
            brain.setMemory(MemoryModuleType.TEMPTING_PLAYER, temptingPlayers.get(0));
        }
    }

    private boolean beryllium$playerHoldingTemptation(Player player) {
        return this.beryllium$isTemptation(player.getMainHandItem()) || this.beryllium$isTemptation(player.getOffhandItem());
    }

    private boolean beryllium$isTemptation(ItemStack stack) {
        return this.temptations.test(stack);
    }
}
