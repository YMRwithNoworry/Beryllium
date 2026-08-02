package alku.beryllium.mixin;

import alku.beryllium.client.ClientInputLatency;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFWCharModsCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(KeyboardHandler.class)
public class KeyboardInputLatencyMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private void charTyped(long window, int codePoint, int modifiers) {
    }

    @ModifyArgs(
        method = "setup",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/InputConstants;setupKeyboardCallbacks(JLorg/lwjgl/glfw/GLFWKeyCallbackI;Lorg/lwjgl/glfw/GLFWCharModsCallbackI;)V"
        )
    )
    private void beryllium$useAllocationFreeCallbacks(Args args) {
        if (!ClientInputLatency.isEnabled()) {
            return;
        }

        GLFWKeyCallbackI scheduledKeyCallback = args.get(1);
        GLFWCharModsCallbackI scheduledCharCallback = args.get(2);
        args.set(1, (GLFWKeyCallbackI) (window, key, scanCode, action, modifiers) -> {
            if (this.minecraft.isSameThread()) {
                ((KeyboardHandler) (Object) this).keyPress(window, key, scanCode, action, modifiers);
            } else {
                scheduledKeyCallback.invoke(window, key, scanCode, action, modifiers);
            }
        });
        args.set(2, (GLFWCharModsCallbackI) (window, codePoint, modifiers) -> {
            if (this.minecraft.isSameThread()) {
                this.charTyped(window, codePoint, modifiers);
            } else {
                scheduledCharCallback.invoke(window, codePoint, modifiers);
            }
        });
    }

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
