package alku.beryllium.client;

public final class InputLatencyGateVerifier {
    private InputLatencyGateVerifier() {
    }

    public static void main(String[] args) {
        preservesRepeatedClicksWithinOneVanillaHandlingPass();
        blocksRepeatedHandlingWithinOneTick();
        defersTargetedInputUntilFrameTargetingIsCurrent();
        defersSecondAttackUntilTheNextTick();
        defersSecondUseUntilTheNextTick();
        flushesReleasesWithoutRepeatingHeldActions();
        blocksOtherFlushesWhileADeferredClickIsQueued();
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
        check(!gate.shouldFlush(true, true, false, true), "attack must wait for current-frame targeting");
        check(gate.takeDeferredTargetedInput(), "the first attack must flush before rendering");
        gate.beginHandling();
        check(gate.allowAttack(), "the frame-aligned attack must run");
        gate.endHandling();
    }

    private static void defersSecondAttackUntilTheNextTick() {
        InputLatencyGate gate = handledAttackGate();
        check(!gate.shouldFlush(true, true, false, true), "a second attack must remain queued");
        check(!gate.takeDeferredTargetedInput(), "a second attack must not bypass the vanilla tick rate");
        gate.prepareTickHandling();
        gate.beginHandling();
        check(gate.allowAttack(), "the queued attack must run on the next vanilla tick");
        check(gate.allowContinueAttack(), "block breaking must resume on the next vanilla tick");
        gate.endHandling();
        gate.finishTickHandling();
        check(!gate.shouldFlush(true, true, false, true), "the following attack must wait for frame targeting");
        check(gate.takeDeferredTargetedInput(), "the following tick interval must accept a new attack");
    }

    private static void defersSecondUseUntilTheNextTick() {
        InputLatencyGate gate = new InputLatencyGate();
        gate.beginHandling();
        check(gate.allowUse(), "first use must run immediately");
        gate.endHandling();
        check(!gate.shouldFlush(true, false, true, true), "a second use must remain queued");
        check(!gate.takeDeferredTargetedInput(), "a second use must not bypass the vanilla tick rate");
        gate.prepareTickHandling();
        gate.beginHandling();
        check(gate.allowUse(), "the queued use must run on the next vanilla tick");
        gate.endHandling();
    }

    private static void flushesReleasesWithoutRepeatingHeldActions() {
        InputLatencyGate gate = handledAttackGate();
        check(gate.shouldFlush(false, false, false, false), "a release must flush immediately");
        gate.beginHandling();
        check(!gate.allowContinueAttack(), "an extra held attack must remain blocked");
        gate.endHandling();
    }

    private static void blocksOtherFlushesWhileADeferredClickIsQueued() {
        InputLatencyGate gate = handledAttackGate();
        check(!gate.shouldFlush(true, true, false, true), "the repeated attack must be deferred");
        check(!gate.shouldFlush(true, false, false, false), "other input must not consume the queued attack");
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
}
