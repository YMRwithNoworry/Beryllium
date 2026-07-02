package alku.beryllium.mixin;

import alku.beryllium.compute.AxolotlAttackableSearch;
import alku.beryllium.compute.FrogAttackableSearch;
import alku.beryllium.compute.VillagerHostileSearch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.AxolotlAttackablesSensor;
import net.minecraft.world.entity.ai.sensing.FrogAttackablesSensor;
import net.minecraft.world.entity.ai.sensing.NearestVisibleLivingEntitySensor;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NearestVisibleLivingEntitySensor.class)
public class NearestVisibleLivingEntitySensorMixin {
    @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
    private void beryllium$batchNearestVisibleDistance(ServerLevel level, LivingEntity entity, CallbackInfo callback) {
        if ((Object) this instanceof AxolotlAttackablesSensor) {
            entity.getBrain().setMemory(MemoryModuleType.NEAREST_ATTACKABLE, AxolotlAttackableSearch.findNearest(entity));
            callback.cancel();
            return;
        }

        if ((Object) this instanceof FrogAttackablesSensor) {
            entity.getBrain().setMemory(MemoryModuleType.NEAREST_ATTACKABLE, FrogAttackableSearch.findNearest(entity));
            callback.cancel();
            return;
        }

        if ((Object) this instanceof VillagerHostilesSensor) {
            entity.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, VillagerHostileSearch.findNearest(entity));
            callback.cancel();
        }
    }
}
