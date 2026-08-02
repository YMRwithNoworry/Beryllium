package alku.beryllium.mixin;

import alku.beryllium.client.ClientInputLatency;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(MouseHandler.class)
public class MouseButtonInputLatencyMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private void onMove(long window, double x, double y) {
    }

    @Shadow
    private void onPress(long window, int button, int action, int modifiers) {
    }

    @Shadow
    private void onScroll(long window, double horizontal, double vertical) {
    }

    @ModifyArgs(
        method = "setup",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/InputConstants;setupMouseCallbacks(JLorg/lwjgl/glfw/GLFWCursorPosCallbackI;Lorg/lwjgl/glfw/GLFWMouseButtonCallbackI;Lorg/lwjgl/glfw/GLFWScrollCallbackI;Lorg/lwjgl/glfw/GLFWDropCallbackI;)V"
        )
    )
    private void beryllium$useAllocationFreeCallbacks(Args args) {
        if (!ClientInputLatency.isEnabled()) {
            return;
        }

        GLFWCursorPosCallbackI scheduledMoveCallback = args.get(1);
        GLFWMouseButtonCallbackI scheduledButtonCallback = args.get(2);
        GLFWScrollCallbackI scheduledScrollCallback = args.get(3);
        args.set(1, (GLFWCursorPosCallbackI) (window, x, y) -> {
            if (this.minecraft.isSameThread()) {
                this.onMove(window, x, y);
            } else {
                scheduledMoveCallback.invoke(window, x, y);
            }
        });
        args.set(2, (GLFWMouseButtonCallbackI) (window, button, action, modifiers) -> {
            if (this.minecraft.isSameThread()) {
                this.onPress(window, button, action, modifiers);
            } else {
                scheduledButtonCallback.invoke(window, button, action, modifiers);
            }
        });
        args.set(3, (GLFWScrollCallbackI) (window, horizontal, vertical) -> {
            if (this.minecraft.isSameThread()) {
                this.onScroll(window, horizontal, vertical);
            } else {
                scheduledScrollCallback.invoke(window, horizontal, vertical);
            }
        });
    }

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
