package alku.beryllium.mixin;

import alku.beryllium.compute.AxolotlAttackableSearch;
import alku.beryllium.compute.VillagerHostileSearch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.AxolotlAttackablesSensor;
import net.minecraft.world.entity.ai.sensing.NearestVisibleLivingEntitySensor;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NearestVisibleLivingEntitySensor.class)
public class NearestVisibleLivingEntitySensorMixin {
    @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
    private void beryllium$batchAxolotlAttackableDistance(ServerLevel level, LivingEntity entity, CallbackInfo callback) {
        if (!((Object) this instanceof AxolotlAttackablesSensor)) {
            if (!((Object) this instanceof VillagerHostilesSensor)) {
                return;
            }

            entity.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, VillagerHostileSearch.findNearest(entity));
            callback.cancel();
            return;
        }

        entity.getBrain().setMemory(MemoryModuleType.NEAREST_ATTACKABLE, AxolotlAttackableSearch.findNearest(entity));
        callback.cancel();
    }
}
