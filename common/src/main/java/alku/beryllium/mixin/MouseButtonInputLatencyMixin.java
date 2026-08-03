package alku.beryllium.mixin;

import alku.beryllium.client.ClientInputLatency;
import alku.beryllium.client.MouseInputLatencyAccess;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.util.SmoothDouble;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(MouseHandler.class)
public class MouseButtonInputLatencyMixin implements MouseInputLatencyAccess {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private boolean ignoreFirstMove;

    @Shadow
    private boolean mouseGrabbed;

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Shadow
    @Final
    private SmoothDouble smoothTurnX;

    @Shadow
    @Final
    private SmoothDouble smoothTurnY;

    @Unique
    private long beryllium$registeredWindow;

    @Unique
    private double beryllium$pendingMoveX;

    @Unique
    private double beryllium$pendingMoveY;

    @Unique
    private boolean beryllium$hasPendingMove;

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
        this.beryllium$drainPendingMove();
        this.beryllium$registeredWindow = args.get(0);
        args.set(1, (GLFWCursorPosCallbackI) (window, x, y) -> {
            if (this.minecraft.isSameThread()) {
                this.beryllium$queueOrHandleMove(window, x, y);
            } else {
                this.minecraft.execute(() -> {
                    this.beryllium$drainPendingMove();
                    scheduledMoveCallback.invoke(window, x, y);
                });
            }
        });
        args.set(2, (GLFWMouseButtonCallbackI) (window, button, action, modifiers) -> {
            if (this.minecraft.isSameThread()) {
                this.beryllium$drainPendingMove();
                this.onPress(window, button, action, modifiers);
            } else {
                this.minecraft.execute(() -> {
                    this.beryllium$drainPendingMove();
                    scheduledButtonCallback.invoke(window, button, action, modifiers);
                });
            }
        });
        args.set(3, (GLFWScrollCallbackI) (window, horizontal, vertical) -> {
            if (this.minecraft.isSameThread()) {
                this.beryllium$drainPendingMove();
                this.onScroll(window, horizontal, vertical);
            } else {
                this.minecraft.execute(() -> {
                    this.beryllium$drainPendingMove();
                    scheduledScrollCallback.invoke(window, horizontal, vertical);
                });
            }
        });
    }

    @Override
    public boolean beryllium$prepareTargetedInput() {
        this.beryllium$drainPendingMove();
        if (!this.mouseGrabbed || !this.minecraft.isWindowActive()) {
            return false;
        }

        boolean hasMovement = this.accumulatedDX != 0.0 || this.accumulatedDY != 0.0;
        boolean smoothCamera = this.minecraft.options.smoothCamera;
        boolean smoothersSettled = !smoothCamera || !hasMovement && this.beryllium$isSettled(this.smoothTurnX)
            && this.beryllium$isSettled(this.smoothTurnY);
        if (smoothCamera && !MouseInputLatencyAccess.canSynchronizeTargeting(true, hasMovement, smoothersSettled)) {
            return false;
        }

        if (hasMovement) {
            ((MouseHandler) (Object) this).handleAccumulatedMovement();
        }
        return true;
    }

    @Unique
    private void beryllium$queueOrHandleMove(long window, double x, double y) {
        if (window == this.beryllium$registeredWindow && this.mouseGrabbed && !this.ignoreFirstMove
            && this.minecraft.isWindowActive()) {
            this.beryllium$pendingMoveX = x;
            this.beryllium$pendingMoveY = y;
            if (!this.beryllium$hasPendingMove) {
                this.beryllium$hasPendingMove = true;
            }
            return;
        }

        this.beryllium$drainPendingMove();
        this.onMove(window, x, y);
    }

    @Unique
    private void beryllium$drainPendingMove() {
        if (!this.beryllium$hasPendingMove) {
            return;
        }

        this.beryllium$hasPendingMove = false;
        this.onMove(this.beryllium$registeredWindow, this.beryllium$pendingMoveX, this.beryllium$pendingMoveY);
    }

    @ModifyExpressionValue(
        method = "handleAccumulatedMovement",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/Blaze3D;getTime()D"
        )
    )
    private double beryllium$drainMoveBeforeConsumption(double currentTime) {
        this.beryllium$drainPendingMove();
        return currentTime;
    }

    @WrapWithCondition(
        method = "handleAccumulatedMovement",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/MouseHandler;turnPlayer(D)V"
        )
    )
    private boolean beryllium$shouldRunPlayerTurn(MouseHandler mouseHandler, double elapsedTime) {
        boolean hasMovement = this.accumulatedDX != 0.0 || this.accumulatedDY != 0.0;
        boolean smoothCamera = this.minecraft.options.smoothCamera;
        boolean smoothersSettled = !smoothCamera || !hasMovement && this.beryllium$isSettled(this.smoothTurnX)
            && this.beryllium$isSettled(this.smoothTurnY);
        return ClientInputLatency.shouldRunMouseTurn(smoothCamera, hasMovement, smoothersSettled);
    }

    @Unique
    private boolean beryllium$isSettled(SmoothDouble smoother) {
        SmoothDoubleAccessor accessor = (SmoothDoubleAccessor) smoother;
        return accessor.beryllium$targetValue() == 0.0 && accessor.beryllium$remainingValue() == 0.0
            && accessor.beryllium$lastAmount() == 0.0;
    }

    @Inject(method = {"setIgnoreFirstMove", "cursorEntered"}, at = @At("HEAD"))
    private void beryllium$drainMoveBeforeIgnoringNextEvent(CallbackInfo ci) {
        this.beryllium$drainPendingMove();
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
        ClientInputLatency.flushFromCallback(
            this.minecraft,
            createsClick,
            attackInput,
            useInput,
            targetedInput
        );
    }
}
