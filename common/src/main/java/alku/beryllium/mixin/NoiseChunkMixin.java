package alku.beryllium.mixin;

import alku.beryllium.api.NoiseChunkInterpolationAccess;
import alku.beryllium.api.NoiseInterpolatorAccess;
import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.worldgen.InterpolationScratch;
import alku.beryllium.worldgen.SlabCache;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMixin implements NoiseChunkInterpolationAccess {
    @Unique
    private static final ThreadLocal<InterpolationScratch> BERYLLIUM$SCRATCH =
        ThreadLocal.withInitial(InterpolationScratch::new);

    @Unique
    private static final ThreadLocal<SlabCache> BERYLLIUM$SLAB_CACHE =
        ThreadLocal.withInitial(SlabCache::new);

    @Shadow
    @Final
    private List<NoiseChunk.NoiseInterpolator> interpolators;

    @Shadow
    @Final
    private int cellWidth;

    @Shadow
    @Final
    private int cellHeight;

    @Shadow
    @Final
    private int cellCountY;

    @Shadow
    @Final
    private int cellCountXZ;

    @Shadow
    private int cellStartBlockX;

    @Shadow
    private int cellStartBlockY;

    @Shadow
    private int cellStartBlockZ;

    @Shadow
    private int inCellX;

    @Shadow
    private int inCellY;

    @Shadow
    private int inCellZ;

    @Shadow
    private long interpolationCounter;

    @Unique
    private boolean beryllium$nativeCellReady;

    @Unique
    private boolean beryllium$nativeSlabReady;

    @Unique
    private int beryllium$nativeCellIndex;

    @Unique
    private long beryllium$slabCacheKey;

    @Override
    public int beryllium$nativeCellIndex() {
        return this.beryllium$nativeCellIndex;
    }

    @Inject(method = "selectCellYZ", at = @At("HEAD"))
    private void beryllium$clearPreviousCell(int y, int z, CallbackInfo callback) {
        this.beryllium$nativeCellReady = false;
        for (NoiseChunk.NoiseInterpolator interpolator : this.interpolators) {
            ((NoiseInterpolatorAccess) (Object) interpolator).beryllium$clearNativeCell();
        }
    }

    @Inject(method = "advanceCellX", at = @At("HEAD"))
    private void beryllium$clearPreviousSlab(int increment, CallbackInfo callback) {
        this.beryllium$nativeSlabReady = false;
        this.beryllium$nativeCellReady = false;
        for (NoiseChunk.NoiseInterpolator interpolator : this.interpolators) {
            ((NoiseInterpolatorAccess) (Object) interpolator).beryllium$clearNativeCell();
        }
    }

    @Inject(method = "advanceCellX", at = @At("RETURN"))
    private void beryllium$prepareNativeSlab(int increment, CallbackInfo callback) {
        int interpolatorCount = this.interpolators.size();
        if (interpolatorCount == 0 || !NativeBridge.isLoaded()) {
            return;
        }

        try {
            // 计算当前 slab 的缓存键（基于 interpolationCounter 和位置）
            long cacheKey = this.interpolationCounter ^ ((long) this.cellStartBlockX << 32);

            SlabCache cache = BERYLLIUM$SLAB_CACHE.get();
            if (cache.isValid(cacheKey, interpolatorCount, this.cellCountY, this.cellCountXZ, this.cellWidth, this.cellHeight)) {
                // 缓存命中，直接使用缓存的结果
                this.beryllium$slabCacheKey = cacheKey;
                this.beryllium$nativeSlabReady = true;
                return;
            }

            // 批量处理整个 slab，而不是逐个 cell
            int cellCount = Math.multiplyExact(this.cellCountY, this.cellCountXZ);
            int interpolationCount = Math.multiplyExact(interpolatorCount, cellCount);
            int cornersLength = Math.multiplyExact(interpolationCount, 8);
            int cellVolume = Math.multiplyExact(Math.multiplyExact(this.cellWidth, this.cellWidth), this.cellHeight);
            int outputLength = Math.multiplyExact(interpolationCount, cellVolume);

            InterpolationScratch scratch = BERYLLIUM$SCRATCH.get();
            double[] corners = scratch.corners(cornersLength);
            double[] values = scratch.values(outputLength);

            // 批量写入所有 interpolator 的 corners
            for (int index = 0; index < interpolatorCount; index++) {
                ((NoiseInterpolatorAccess) (Object) this.interpolators.get(index)).beryllium$writeSlabCorners(
                    corners,
                    index,
                    interpolatorCount,
                    this.cellCountY,
                    this.cellCountXZ
                );
            }

            // 一次性插值整个 slab 的所有 cells
            if (!NativeBridge.tryInterpolateDensityCells(
                corners,
                interpolationCount,
                this.cellWidth,
                this.cellHeight,
                values
            )) {
                return;
            }

            // 更新缓存
            cache.update(cacheKey, interpolatorCount, this.cellCountY, this.cellCountXZ,
                        this.cellWidth, this.cellHeight, values);
            this.beryllium$slabCacheKey = cacheKey;
            this.beryllium$nativeSlabReady = true;
        } catch (ArithmeticException ignored) {
            // 溢出时回退到 vanilla 逻辑
        }
    }

    @Inject(method = "selectCellYZ", at = @At("RETURN"))
    private void beryllium$selectNativeCell(int y, int z, CallbackInfo callback) {
        if (!this.beryllium$nativeSlabReady) {
            return;
        }

        int interpolatorCount = this.interpolators.size();
        int cellVolume = Math.multiplyExact(Math.multiplyExact(this.cellWidth, this.cellWidth), this.cellHeight);
        int cellIndex = y * this.cellCountXZ + z;

        // 优先使用缓存的数据
        SlabCache cache = BERYLLIUM$SLAB_CACHE.get();
        double[] values = cache.isValid(this.beryllium$slabCacheKey, interpolatorCount, this.cellCountY, this.cellCountXZ, this.cellWidth, this.cellHeight)
            ? cache.values
            : BERYLLIUM$SCRATCH.get().values;

        for (int index = 0; index < interpolatorCount; index++) {
            ((NoiseInterpolatorAccess) (Object) this.interpolators.get(index)).beryllium$useNativeCell(
                values,
                (cellIndex * interpolatorCount + index) * cellVolume
            );
        }
        this.beryllium$nativeCellReady = true;
    }

    @Inject(method = "updateForY", at = @At("HEAD"), cancellable = true)
    private void beryllium$skipInterpolatorYUpdates(int cellEndBlockY, double y, CallbackInfo callback) {
        if (this.beryllium$nativeCellReady) {
            this.inCellY = cellEndBlockY - this.cellStartBlockY;
            this.beryllium$nativeCellIndex = this.inCellY * this.cellWidth * this.cellWidth;
            callback.cancel();
        }
    }

    @Inject(method = "updateForX", at = @At("HEAD"), cancellable = true)
    private void beryllium$skipInterpolatorXUpdates(int cellEndBlockX, double x, CallbackInfo callback) {
        if (this.beryllium$nativeCellReady) {
            this.inCellX = cellEndBlockX - this.cellStartBlockX;
            this.beryllium$nativeCellIndex = (this.inCellY * this.cellWidth + this.inCellX) * this.cellWidth;
            callback.cancel();
        }
    }

    @Inject(method = "updateForZ", at = @At("HEAD"), cancellable = true)
    private void beryllium$skipInterpolatorZUpdates(int cellEndBlockZ, double z, CallbackInfo callback) {
        if (this.beryllium$nativeCellReady) {
            this.inCellZ = cellEndBlockZ - this.cellStartBlockZ;
            this.beryllium$nativeCellIndex = (this.inCellY * this.cellWidth + this.inCellX) * this.cellWidth + this.inCellZ;
            this.interpolationCounter++;
            callback.cancel();
        }
    }
}
