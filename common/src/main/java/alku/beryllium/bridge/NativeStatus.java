package alku.beryllium.bridge;

/**
 * Result status for native bridge calls.
 */
public enum NativeStatus {
    /** Native call completed successfully. */
    OK(0),
    /** Input validation failed on the Java or native side. */
    INVALID_INPUT(1),
    /** Native output buffer validation failed. */
    OUTPUT_LENGTH_MISMATCH(2),
    /** FFM symbol lookup or downcall failed. */
    FFM_ERROR(3),
    /** Native library is not available. */
    UNAVAILABLE(4);

    private final int code;

    NativeStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean isSuccess() {
        return this == OK;
    }

    public static NativeStatus fromCode(int code) {
        return switch (code) {
            case 0 -> OK;
            case 1 -> INVALID_INPUT;
            case 2 -> OUTPUT_LENGTH_MISMATCH;
            case 3 -> FFM_ERROR;
            default -> UNAVAILABLE;
        };
    }
}
