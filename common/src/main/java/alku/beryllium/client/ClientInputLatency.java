package alku.beryllium.client;

import alku.beryllium.mixin.MinecraftInputLatencyAccess;
import alku.beryllium.mixin.MouseInputLatencyAccess;
import net.minecraft.client.Minecraft;

public final class ClientInputLatency {
    private static final boolean ENABLED = !isClassPresent("dev.kemmlow.inputoptimizer.InputFlushManager")
        && !isClassPresent("me.incend1um.noinputlagtickrate.NoInputLagTickRateClient");
    private static final InputLatencyGate GATE = new InputLatencyGate();

    private static boolean flushing;

    private ClientInputLatency() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void flushFromCallback(
        Minecraft minecraft,
        boolean createsClick,
        boolean attackInput,
        boolean useInput,
        boolean targetedInput
    ) {
        if (!ENABLED || flushing || !minecraft.isSameThread()) {
            return;
        }
        if (!canHandleGameplayInput(minecraft)) {
            GATE.reset();
            return;
        }
        MouseInputLatencyAccess targetingPreparation = createsClick && targetedInput
            ? (MouseInputLatencyAccess) minecraft.mouseHandler
            : null;
        int flushPlan = GATE.planFlush(createsClick, attackInput, useInput, targetedInput, targetingPreparation);
        if (flushPlan == InputLatencyGate.DEFER) {
            return;
        }

        if (flushPlan == InputLatencyGate.FLUSH_WITH_CURRENT_TARGET) {
            minecraft.gameRenderer.pick(1.0F);
        }
        invokeHandleKeybinds(minecraft);
    }

    public static void flushDeferredTargetedInput(Minecraft minecraft) {
        if (!ENABLED || flushing || !minecraft.isSameThread()) {
            return;
        }
        if (!canHandleGameplayInput(minecraft)) {
            GATE.reset();
            return;
        }
        if (!GATE.takeDeferredTargetedInput()) {
            return;
        }

        minecraft.gameRenderer.pick(1.0F);
        invokeHandleKeybinds(minecraft);
    }

    private static void invokeHandleKeybinds(Minecraft minecraft) {
        flushing = true;
        try {
            ((MinecraftInputLatencyAccess) minecraft).beryllium$handleKeybinds();
        } finally {
            flushing = false;
        }
    }

    private static boolean canHandleGameplayInput(Minecraft minecraft) {
        return minecraft.screen == null && minecraft.getOverlay() == null && !minecraft.isPaused()
            && minecraft.level != null && minecraft.player != null && minecraft.gameMode != null;
    }

    public static void prepareTickHandling() {
        if (ENABLED) {
            GATE.prepareTickHandling();
        }
    }

    public static void finishTickHandling() {
        if (ENABLED) {
            GATE.finishTickHandling();
        }
    }

    public static void beginHandling() {
        if (ENABLED) {
            GATE.beginHandling();
        }
    }

    public static void endHandling() {
        if (ENABLED) {
            GATE.endHandling();
        }
    }

    public static boolean allowAttack() {
        return !ENABLED || GATE.allowAttack();
    }

    public static boolean allowContinueAttack() {
        return !ENABLED || GATE.allowContinueAttack();
    }

    public static boolean allowUse() {
        return !ENABLED || GATE.allowUse();
    }

    public static boolean shouldRunMouseTurn(boolean smoothCamera, boolean hasMovement, boolean smoothersSettled) {
        return !ENABLED || GATE.shouldRunMouseTurn(smoothCamera, hasMovement, smoothersSettled);
    }

    public static boolean canSynchronizeTargeting(boolean smoothCamera, boolean hasMovement, boolean smoothersSettled) {
        return ENABLED && GATE.canSynchronizeTargeting(smoothCamera, hasMovement, smoothersSettled);
    }

    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name, false, ClientInputLatency.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
