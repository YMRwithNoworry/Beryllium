package alku.beryllium.mixin;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = LongOpenHashSet.class, remap = false)
public abstract class LongOpenHashSetMixin implements LongOpenHashSetAccess {
    @Shadow(remap = false)
    protected transient long[] key;

    @Shadow(remap = false)
    protected transient boolean containsNull;

    @Override
    @Unique
    public long[] beryllium$getKeyTable() {
        return this.key;
    }

    @Override
    @Unique
    public boolean beryllium$containsNull() {
        return this.containsNull;
    }
}
