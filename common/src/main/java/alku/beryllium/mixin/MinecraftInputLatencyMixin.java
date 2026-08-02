package alku.beryllium.mixin;

import alku.beryllium.client.ClientInputLatency;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftInputLatencyMixin {
    @Shadow
    @Final
    public MouseHandler mouseHandler;

    @Unique
    private boolean beryllium$handledMouseMovementEarly;

    @ModifyVariable(method = "runTick(Z)V", at = @At("HEAD"), argsOnly = true)
    private boolean beryllium$handleGameplayMouseMovementEarly(boolean renderLevel) {
        this.beryllium$handledMouseMovementEarly = ClientInputLatency.isEnabled() && this.mouseHandler.isMouseGrabbed();
        if (this.beryllium$handledMouseMovementEarly) {
            this.mouseHandler.handleAccumulatedMovement();
        }
        return renderLevel;
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
        ClientInputLatency.flushDeferredTargetedInput((Minecraft) (Object) this);
    }

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;handleKeybinds()V",
            shift = At.Shift.BEFORE
        )
    )
    private void beryllium$prepareTickInputHandling(CallbackInfo ci) {
        ClientInputLatency.prepareTickHandling();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void beryllium$finishTickInputHandling(CallbackInfo ci) {
        ClientInputLatency.finishTickHandling();
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void beryllium$beginInputHandling(CallbackInfo ci) {
        ClientInputLatency.beginHandling();
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void beryllium$endInputHandling(CallbackInfo ci) {
        ClientInputLatency.endHandling();
    }

    @Inject(method = "startAttack()Z", at = @At("HEAD"), cancellable = true)
    private void beryllium$limitAttackToVanillaTickRate(CallbackInfoReturnable<Boolean> cir) {
        if (!ClientInputLatency.allowAttack()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack(Z)V", at = @At("HEAD"), cancellable = true)
    private void beryllium$limitBlockBreakingToVanillaTickRate(boolean attacking, CallbackInfo ci) {
        if (attacking && !ClientInputLatency.allowContinueAttack()) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem()V", at = @At("HEAD"), cancellable = true)
    private void beryllium$limitUseToVanillaTickRate(CallbackInfo ci) {
        if (!ClientInputLatency.allowUse()) {
            ci.cancel();
        }
    }
}
