package alku.beryllium.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.FrogAttackablesSensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.animal.frog.Frog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mirrors Lithium's 1.21.1 frog sensor optimization by rejecting non-food targets before the expensive attackability test.
 */
@Mixin(FrogAttackablesSensor.class)
public class FrogAttackablesSensorMixin {
    @Redirect(
        method = "isMatchingEntity(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/sensing/Sensor;isEntityAttackable(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z"
        )
    )
    private boolean beryllium$checkFoodTypeBeforeAttackability(LivingEntity entity, LivingEntity target) {
        return Frog.canEat(target) && Sensor.isEntityAttackable(entity, target);
    }

    @Redirect(
        method = "isMatchingEntity(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/animal/frog/Frog;canEat(Lnet/minecraft/world/entity/LivingEntity;)Z"
        ),
        require = 0
    )
    private boolean beryllium$skipRepeatedFoodTypeCheck(LivingEntity target) {
        return true;
    }
}
