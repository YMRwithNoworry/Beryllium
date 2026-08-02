package alku.beryllium.mixin;

import alku.beryllium.client.ClientInputLatency;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseButtonInputLatencyMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onPress(JIII)V", at = @At("TAIL"))
    private void beryllium$flushGameplayInput(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (window != this.minecraft.getWindow().getWindow()) {
            return;
        }

        boolean createsClick = action != 0;
        int mappedButton = Minecraft.ON_OSX && createsClick && button == 0 && (modifiers & 2) == 2 ? 1 : button;
        boolean attackInput = createsClick && this.minecraft.options.keyAttack.matchesMouse(mappedButton);
        boolean useInput = createsClick && this.minecraft.options.keyUse.matchesMouse(mappedButton);
        boolean targetedInput = attackInput || useInput
            || createsClick && this.minecraft.options.keyPickItem.matchesMouse(mappedButton);
        ClientInputLatency.flushFromCallback(this.minecraft, createsClick, attackInput, useInput, targetedInput);
    }
}
