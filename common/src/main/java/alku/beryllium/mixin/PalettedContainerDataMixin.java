package alku.beryllium.mixin;

import alku.beryllium.compute.PalettedContainerPacker;
import alku.beryllium.compute.NativeBatching;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes vanilla paletted-container network serialization through the Rust packer.
 *
 * <p>The vanilla path writes the bits byte, then the palette varints, then one virtual
 * {@code writeLong} per packed long. The native path packs the identical byte sequence in one
 * FFM call and appends it with a single bulk {@code writeBytes}. Below the measured threshold,
 * for unknown palette implementations, and on any native failure the original method runs
 * unchanged.</p>
 */
@Mixin(targets = "net.minecraft.world.level.chunk.PalettedContainer$Data")
public class PalettedContainerDataMixin {
    @Unique
    private static final ThreadLocal<byte[]> BERYLLIUM_PACK_SCRATCH = ThreadLocal.withInitial(() -> new byte[512]);
    @Unique
    private static final ThreadLocal<int[]> BERYLLIUM_PALETTE_SCRATCH = ThreadLocal.withInitial(() -> new int[16]);

    @Shadow
    @Final
    BitStorage storage;

    @Shadow
    @Final
    Palette<?> palette;

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void beryllium$writeNative(FriendlyByteBuf buffer, CallbackInfo callbackInfo) {
        long[] raw = this.storage.getRaw();
        if (!NativeBatching.shouldUseNativePalettedContainerPack(raw.length)) {
            return;
        }

        int[] paletteIds = BERYLLIUM_PALETTE_SCRATCH.get();
        int[] scratchIds = this.palette instanceof net.minecraft.world.level.chunk.GlobalPalette
            ? null
            : paletteIds;
        int maximumSize = PalettedContainerPacker.maximumPackedSize(paletteIds.length, raw.length);
        byte[] scratch = BERYLLIUM_PACK_SCRATCH.get();
        if (scratch.length < maximumSize) {
            scratch = new byte[maximumSize];
            BERYLLIUM_PACK_SCRATCH.set(scratch);
        }

        int packed = PalettedContainerPacker.pack(this.storage, this.palette, raw, scratch, scratchIds);
        if (packed < 0) {
            return;
        }
        buffer.writeBytes(scratch, 0, packed);
        callbackInfo.cancel();
    }
}
