package alku.beryllium.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftInputLatencyMixin {
    @Shadow
    @Final
    public MouseHandler mouseHandler;

    @Unique
    private boolean beryllium$handledMouseMovementEarly;

    @Inject(method = "runTick(Z)V", at = @At("HEAD"))
    private void beryllium$handleGameplayMouseMovementEarly(boolean renderLevel, CallbackInfo ci) {
        this.beryllium$handledMouseMovementEarly = this.mouseHandler.isMouseGrabbed();
        if (this.beryllium$handledMouseMovementEarly) {
            this.mouseHandler.handleAccumulatedMovement();
        }
    }

    @Redirect(
        method = "runTick(Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/MouseHandler;handleAccumulatedMovement()V"
        )
    )
    private void beryllium$avoidDuplicateMouseMovementHandling(MouseHandler mouseHandler) {
        if (!this.beryllium$handledMouseMovementEarly) {
            mouseHandler.handleAccumulatedMovement();
        }
        this.beryllium$handledMouseMovementEarly = false;
    }
}
