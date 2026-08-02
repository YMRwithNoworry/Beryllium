package alku.beryllium.client;

final class InputLatencyGate {
    private static final int ATTACK = 1;
    private static final int USE = 1 << 1;
    private static final int TARGETED = 1 << 2;

    private boolean attackHandled;
    private boolean continueAttackHandled;
    private boolean useHandled;
    private int deferredActions;

    private boolean blockAttack;
    private boolean blockContinueAttack;
    private boolean blockUse;
    private boolean attackOccurred;
    private boolean continueAttackOccurred;
    private boolean useOccurred;

    boolean shouldFlush(boolean createsClick, boolean attackInput, boolean useInput, boolean targetedInput) {
        int actions = (attackInput ? ATTACK : 0) | (useInput ? USE : 0) | (targetedInput ? TARGETED : 0);
        if (this.deferredActions != 0) {
            if (createsClick) {
                this.deferredActions |= actions;
            }
            return false;
        }

        if (createsClick && targetedInput) {
            this.deferredActions = actions;
            return false;
        }
        return true;
    }

    boolean takeDeferredTargetedInput() {
        if (this.deferredActions == 0) {
            return false;
        }
        if ((this.deferredActions & ATTACK) != 0 && (this.attackHandled || this.continueAttackHandled)) {
            return false;
        }
        if ((this.deferredActions & USE) != 0 && this.useHandled) {
            return false;
        }
        this.deferredActions = 0;
        return true;
    }

    void prepareTickHandling() {
        if ((this.deferredActions & ATTACK) != 0) {
            this.attackHandled = false;
            this.continueAttackHandled = false;
        }
        if ((this.deferredActions & USE) != 0) {
            this.useHandled = false;
        }
        this.deferredActions = 0;
    }

    void finishTickHandling() {
        this.reset();
    }

    void beginHandling() {
        this.blockAttack = this.attackHandled;
        this.blockContinueAttack = this.continueAttackHandled;
        this.blockUse = this.useHandled;
        this.attackOccurred = false;
        this.continueAttackOccurred = false;
        this.useOccurred = false;
    }

    void endHandling() {
        this.attackHandled |= this.attackOccurred;
        this.continueAttackHandled |= this.continueAttackOccurred;
        this.useHandled |= this.useOccurred;
    }

    boolean allowAttack() {
        if (this.blockAttack) {
            return false;
        }
        this.attackOccurred = true;
        return true;
    }

    boolean allowContinueAttack() {
        if (this.blockContinueAttack) {
            return false;
        }
        this.continueAttackOccurred = true;
        return true;
    }

    boolean allowUse() {
        if (this.blockUse) {
            return false;
        }
        this.useOccurred = true;
        return true;
    }

    void reset() {
        this.attackHandled = false;
        this.continueAttackHandled = false;
        this.useHandled = false;
        this.deferredActions = 0;
        this.blockAttack = false;
        this.blockContinueAttack = false;
        this.blockUse = false;
        this.attackOccurred = false;
        this.continueAttackOccurred = false;
        this.useOccurred = false;
    }
}
