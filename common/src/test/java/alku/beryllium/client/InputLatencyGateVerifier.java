package alku.beryllium.client;

import alku.beryllium.mixin.MouseButtonInputLatencyMixin;

public final class InputLatencyGateVerifier {
    private InputLatencyGateVerifier() {
    }

    public static void main(String[] args) {
        preservesRepeatedClicksWithinOneVanillaHandlingPass();
        blocksRepeatedHandlingWithinOneTick();
        flushesTargetedInputWhenTargetingIsCurrent();
        defersTargetedInputUntilFrameTargetingIsCurrent();
        defersSecondAttackUntilTheNextTick();
        defersSecondUseUntilTheNextTick();
        flushesOrdinaryClicksWithoutPreparingTargeting();
        flushesReleasesWithoutRepeatingHeldActions();
        blocksOtherFlushesWhileADeferredClickIsQueued();
        coalescedCursorEventsPreserveAccumulatedDelta();
        firstCursorEventRemainsUncoalesced();
        skipsOnlyResetNonSmoothIdleTurns();
        synchronizesOnlyCurrentTargetingState();
        exposesSharedTargetingPreparation();
    }

    private static void exposesSharedTargetingPreparation() {
        check(
            !MouseInputLatencyAccess.class.getPackageName().startsWith("alku.beryllium.mixin"),
            "directly loaded access interfaces must remain outside the defined mixin package"
        );
        check(
            MouseInputLatencyAccess.class.isAssignableFrom(MouseButtonInputLatencyMixin.class),
            "the mouse mixin must expose shared targeting preparation"
        );
    }

    private static void synchronizesOnlyCurrentTargetingState() {
        check(
            MouseInputLatencyAccess.canSynchronizeTargeting(false, true, false),
            "non-smooth movement can be consumed immediately"
        );
        check(MouseInputLatencyAccess.canSynchronizeTargeting(true, false, true), "settled smooth targeting is already current");
        check(!MouseInputLatencyAccess.canSynchronizeTargeting(true, true, true), "new smooth movement must wait for interpolation");
        check(!MouseInputLatencyAccess.canSynchronizeTargeting(true, false, false), "residual smoothing must wait for interpolation");
    }

    private static void skipsOnlyResetNonSmoothIdleTurns() {
        InputLatencyGate gate = new InputLatencyGate();
        check(gate.shouldRunMouseTurn(false, false, true), "the first idle turn must reset both smoothers");
        check(!gate.shouldRunMouseTurn(false, false, true), "a reset non-smooth idle turn must be skipped");
        check(gate.shouldRunMouseTurn(false, true, true), "actual mouse movement must always run");
        check(!gate.shouldRunMouseTurn(false, false, true), "movement must leave smoothers in the reset state");
        check(!gate.shouldRunMouseTurn(true, false, true), "settled smooth camera input must be skipped");
        check(gate.shouldRunMouseTurn(true, false, false), "residual smoothing must advance without new input");
        check(gate.shouldRunMouseTurn(true, true, false), "new smooth-camera movement must ignore smoother state");
        check(gate.shouldRunMouseTurn(false, false, true), "disabling smooth camera must run one reset pass");
        check(!gate.shouldRunMouseTurn(false, false, true), "idle turns after the reset pass must be skipped");
    }

    private static void coalescedCursorEventsPreserveAccumulatedDelta() {
        double[] positions = {12.0, 15.5, 14.0, 23.25, 18.75};
        double originalDelta = 0.0;
        for (int i = 1; i < positions.length; i++) {
            originalDelta += positions[i] - positions[i - 1];
        }
        checkEqual(originalDelta, positions[positions.length - 1] - positions[0], "coalesced cursor delta");
    }

    private static void firstCursorEventRemainsUncoalesced() {
        double positionBeforeGrab = 100.0;
        double ignoredWarpPosition = 500.0;
        double[] positionsAfterWarp = {503.0, 501.0, 508.0};
        double originalDelta = positionsAfterWarp[0] - ignoredWarpPosition;
        for (int i = 1; i < positionsAfterWarp.length; i++) {
            originalDelta += positionsAfterWarp[i] - positionsAfterWarp[i - 1];
        }
        double coalescedDelta = positionsAfterWarp[positionsAfterWarp.length - 1] - ignoredWarpPosition;
        checkEqual(originalDelta, coalescedDelta, "post-warp cursor delta");
        check(
            Double.compare(coalescedDelta, positionsAfterWarp[positionsAfterWarp.length - 1] - positionBeforeGrab) != 0,
            "the first cursor warp must remain a distinct event"
        );
    }

    private static void blocksRepeatedHandlingWithinOneTick() {
        InputLatencyGate gate = new InputLatencyGate();
        gate.beginHandling();
        check(gate.allowAttack(), "first attack handling pass must run");
        check(gate.allowContinueAttack(), "first block-breaking pass must run");
        check(gate.allowUse(), "first use handling pass must run");
        gate.endHandling();
        gate.beginHandling();
        check(!gate.allowAttack(), "second attack handling pass in one tick must be blocked");
        check(!gate.allowContinueAttack(), "second block-breaking pass in one tick must be blocked");
        check(!gate.allowUse(), "second use handling pass in one tick must be blocked");
        gate.endHandling();
    }

    private static void preservesRepeatedClicksWithinOneVanillaHandlingPass() {
        InputLatencyGate gate = new InputLatencyGate();
        gate.beginHandling();
        check(gate.allowAttack(), "first attack in one vanilla pass must run");
        check(gate.allowAttack(), "vanilla must retain multiple queued attacks in one pass");
        check(gate.allowUse(), "first use in one vanilla pass must run");
        check(gate.allowUse(), "vanilla must retain multiple queued uses in one pass");
        gate.endHandling();
    }

    private static void defersTargetedInputUntilFrameTargetingIsCurrent() {
        InputLatencyGate gate = new InputLatencyGate();
        TargetPreparation targeting = new TargetPreparation(false);
        checkEqual(InputLatencyGate.DEFER, gate.planFlush(true, true, false, true, targeting), "stale attack plan");
        checkEqual(1, targeting.calls, "stale attack targeting preparations");
        check(gate.takeDeferredTargetedInput(), "the first attack must flush before rendering");
        gate.beginHandling();
        check(gate.allowAttack(), "the frame-aligned attack must run");
        gate.endHandling();
    }

    private static void flushesTargetedInputWhenTargetingIsCurrent() {
        InputLatencyGate gate = new InputLatencyGate();
        TargetPreparation targeting = new TargetPreparation(true);
        checkEqual(
            InputLatencyGate.FLUSH_WITH_CURRENT_TARGET,
            gate.planFlush(true, true, false, true, targeting),
            "current attack plan"
        );
        checkEqual(1, targeting.calls, "current attack targeting preparations");
        check(!gate.takeDeferredTargetedInput(), "an immediate targeted attack must not remain queued");
    }

    private static void defersSecondAttackUntilTheNextTick() {
        InputLatencyGate gate = handledAttackGate();
        TargetPreparation targeting = new TargetPreparation(true);
        checkEqual(InputLatencyGate.DEFER, gate.planFlush(true, true, false, true, targeting), "second attack plan");
        checkEqual(0, targeting.calls, "blocked attacks must not prepare targeting");
        check(!gate.takeDeferredTargetedInput(), "a second attack must not bypass the vanilla tick rate");
        gate.prepareTickHandling();
        gate.beginHandling();
        check(gate.allowAttack(), "the queued attack must run on the next vanilla tick");
        check(gate.allowContinueAttack(), "block breaking must resume on the next vanilla tick");
        gate.endHandling();
        gate.finishTickHandling();
        TargetPreparation staleTargeting = new TargetPreparation(false);
        checkEqual(InputLatencyGate.DEFER, gate.planFlush(true, true, false, true, staleTargeting), "following attack plan");
        checkEqual(1, staleTargeting.calls, "the following attack must prepare targeting once");
        check(gate.takeDeferredTargetedInput(), "the following tick interval must accept a new attack");
    }

    private static void defersSecondUseUntilTheNextTick() {
        InputLatencyGate gate = new InputLatencyGate();
        gate.beginHandling();
        check(gate.allowUse(), "first use must run immediately");
        gate.endHandling();
        TargetPreparation targeting = new TargetPreparation(true);
        checkEqual(InputLatencyGate.DEFER, gate.planFlush(true, false, true, true, targeting), "second use plan");
        checkEqual(0, targeting.calls, "blocked uses must not prepare targeting");
        check(!gate.takeDeferredTargetedInput(), "a second use must not bypass the vanilla tick rate");
        gate.prepareTickHandling();
        gate.beginHandling();
        check(gate.allowUse(), "the queued use must run on the next vanilla tick");
        gate.endHandling();
    }

    private static void flushesReleasesWithoutRepeatingHeldActions() {
        InputLatencyGate gate = handledAttackGate();
        checkEqual(InputLatencyGate.FLUSH, gate.planFlush(false, false, false, false, null), "release plan");
        gate.beginHandling();
        check(!gate.allowContinueAttack(), "an extra held attack must remain blocked");
        gate.endHandling();
    }

    private static void flushesOrdinaryClicksWithoutPreparingTargeting() {
        InputLatencyGate gate = new InputLatencyGate();
        TargetPreparation targeting = new TargetPreparation(true);
        checkEqual(InputLatencyGate.FLUSH, gate.planFlush(true, false, false, false, targeting), "ordinary click plan");
        checkEqual(0, targeting.calls, "ordinary clicks must not prepare targeting");
    }

    private static void blocksOtherFlushesWhileADeferredClickIsQueued() {
        InputLatencyGate gate = handledAttackGate();
        TargetPreparation targeting = new TargetPreparation(true);
        checkEqual(InputLatencyGate.DEFER, gate.planFlush(true, true, false, true, targeting), "repeated attack plan");
        checkEqual(InputLatencyGate.DEFER, gate.planFlush(true, false, false, false, null), "queued other input plan");
        checkEqual(0, targeting.calls, "queued input must not prepare targeting");
        gate.prepareTickHandling();
        gate.beginHandling();
        check(gate.allowAttack(), "the deferred attack must still be available to vanilla");
        gate.endHandling();
    }

    private static InputLatencyGate handledAttackGate() {
        InputLatencyGate gate = new InputLatencyGate();
        gate.beginHandling();
        check(gate.allowAttack(), "first attack must run immediately");
        check(gate.allowContinueAttack(), "first block-breaking update must run immediately");
        gate.endHandling();
        return gate;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void checkEqual(double expected, double actual, String message) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static final class TargetPreparation implements MouseInputLatencyAccess {
        private final boolean ready;
        private int calls;

        private TargetPreparation(boolean ready) {
            this.ready = ready;
        }

        @Override
        public boolean beryllium$prepareTargetedInput() {
            this.calls++;
            return this.ready;
        }
    }
}
