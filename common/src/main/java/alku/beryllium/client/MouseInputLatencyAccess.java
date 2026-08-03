package alku.beryllium.client;

public interface MouseInputLatencyAccess {
    static boolean canSynchronizeTargeting(boolean smoothCamera, boolean hasMovement, boolean smoothersSettled) {
        return !smoothCamera || !hasMovement && smoothersSettled;
    }

    boolean beryllium$prepareTargetedInput();
}
