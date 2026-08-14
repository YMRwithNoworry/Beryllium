package alku.beryllium.mixin;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PerlinNoise.class)
public interface PerlinNoiseAccessor {
    @Accessor("noiseLevels")
    ImprovedNoise[] beryllium$noiseLevels();

    @Accessor("amplitudes")
    DoubleList beryllium$amplitudes();

    @Accessor("lowestFreqInputFactor")
    double beryllium$lowestFrequencyInputFactor();

    @Accessor("lowestFreqValueFactor")
    double beryllium$lowestFrequencyValueFactor();
}
