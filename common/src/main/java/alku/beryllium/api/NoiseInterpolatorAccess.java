package alku.beryllium.api;

/**
 * Accessor interface for NoiseInterpolator optimization.
 * Implemented by NoiseChunk.NoiseInterpolator via Mixin.
 */
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
