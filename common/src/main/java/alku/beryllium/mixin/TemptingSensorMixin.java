package alku.beryllium.mixin;

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
     * @reason Preserve vanilla predicate order while selecting the nearest tempting player without a full sort.
     * @author YMRwithNoworry
     */
    @Overwrite
    protected void doTick(ServerLevel level, PathfinderMob mob) {
        Brain<?> brain = mob.getBrain();
        ServerPlayer nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!EntitySelector.NO_SPECTATORS.test(player)
                || !TEMPT_TARGETING.test(mob, player)
                || !mob.closerThan(player, 10.0)
                || !this.beryllium$playerHoldingTemptation(player)
                || mob.hasPassenger(player)) {
                continue;
            }

            double distance = mob.distanceToSqr(player);
            if (distance < nearestDistance) {
                nearestPlayer = player;
                nearestDistance = distance;
            }
        }

        if (nearestPlayer == null) {
            brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
        } else {
            brain.setMemory(MemoryModuleType.TEMPTING_PLAYER, nearestPlayer);
        }
    }

    private boolean beryllium$playerHoldingTemptation(Player player) {
        return this.beryllium$isTemptation(player.getMainHandItem()) || this.beryllium$isTemptation(player.getOffhandItem());
    }

    private boolean beryllium$isTemptation(ItemStack stack) {
        return this.temptations.test(stack);
    }
}
