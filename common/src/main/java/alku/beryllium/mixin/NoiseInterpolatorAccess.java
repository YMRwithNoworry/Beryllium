package alku.beryllium.mixin;

public interface NoiseInterpolatorAccess {
    void beryllium$writeSlabCorners(
        double[] output,
        int interpolatorIndex,
        int interpolatorCount,
        int cellCountY,
        int cellCountXZ
    );

    void beryllium$useNativeCell(double[] values, int offset);

    void beryllium$clearNativeCell();
}
