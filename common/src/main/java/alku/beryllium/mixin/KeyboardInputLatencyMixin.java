package alku.beryllium.mixin;

import alku.beryllium.client.ClientInputLatency;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardInputLatencyMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress(JIIII)V", at = @At("TAIL"))
    private void beryllium$flushGameplayInput(
        long window,
        int key,
        int scanCode,
        int action,
        int modifiers,
        CallbackInfo ci
    ) {
        if (window != this.minecraft.getWindow().getWindow()) {
            return;
        }

        boolean createsClick = action != 0;
        boolean attackInput = createsClick && this.minecraft.options.keyAttack.matches(key, scanCode);
        boolean useInput = createsClick && this.minecraft.options.keyUse.matches(key, scanCode);
        boolean targetedInput = attackInput || useInput
            || createsClick && this.minecraft.options.keyPickItem.matches(key, scanCode);
        ClientInputLatency.flushFromCallback(this.minecraft, createsClick, attackInput, useInput, targetedInput);
    }
}
