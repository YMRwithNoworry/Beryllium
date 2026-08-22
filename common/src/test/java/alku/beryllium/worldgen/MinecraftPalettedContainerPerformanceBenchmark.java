package alku.beryllium.worldgen;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.bridge.NativeStatus;
import alku.beryllium.compute.PalettedContainerPacker;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Measures vanilla paletted-container section serialization against the native packer.
 *
 * <p>The Java baseline replicates the exact vanilla {@code Data.write} sequence
 * ({@code writeByte(bits)}, {@code palette.write}, {@code writeLongArray}); the native path is
 * what {@code PalettedContainerDataMixin} does (pack into thread-local scratch, one bulk
 * {@code writeBytes}).</p>
 */
public final class MinecraftPalettedContainerPerformanceBenchmark {
    private static final int WARMUP_ITERATIONS = 300;
    private static final int MEASURE_ITERATIONS = 501;
    private static volatile long blackhole;

    private MinecraftPalettedContainerPerformanceBenchmark() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (NativeBridge.initialize() != NativeStatus.OK) {
            throw new AssertionError("Paletted-container benchmark requires the native backend");
        }
        MinecraftPalettedContainerPackVerifier.verifyNativeMatchesMinecraftReference();

        for (int distinct : new int[] {2, 8, 32, 128, 256, 3_000}) {
            benchmark(distinct);
        }
    }

    private static void benchmark(int distinct) {
        int registrySize = Math.max(distinct * 2, 16);
        MinecraftPalettedContainerPackVerifier.SimpleIdMap registry =
            new MinecraftPalettedContainerPackVerifier.SimpleIdMap(registrySize);
        PalettedContainer<String> container = new PalettedContainer<>(
            registry,
            registry.byId(0),
            PalettedContainer.Strategy.SECTION_STATES
        );
        Random random = new Random(0xBE2A11L + distinct);
        for (int index = 0; index < 4_096; index++) {
            container.set(index & 15, (index >> 4) & 15, (index >> 8) & 15, registry.byId(random.nextInt(distinct)));
        }

        Object data = MinecraftPalettedContainerPackVerifier.containerData(container);
        BitStorage storage = MinecraftPalettedContainerPackVerifier.containerStorage(data);
        Palette<?> palette = MinecraftPalettedContainerPackVerifier.containerPalette(data);
        long[] raw = storage.getRaw();
        byte[] scratch = new byte[PalettedContainerPacker.maximumPackedSize(512, raw.length)];
        int[] paletteScratch = new int[512];

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            Runnable javaWork = () -> {
                buffer.writeByte(storage.getBits());
                palette.write(buffer);
                buffer.writeLongArray(raw);
                blackhole = buffer.readableBytes();
                buffer.clear();
            };
            Runnable nativeWork = () -> {
                int packed = PalettedContainerPacker.pack(storage, palette, raw, scratch, paletteScratch);
                if (packed < 0) {
                    throw new AssertionError("native pack failed");
                }
                buffer.writeBytes(scratch, 0, packed);
                blackhole = buffer.readableBytes();
                buffer.clear();
            };

            for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
                if ((iteration & 1) == 0) {
                    javaWork.run();
                    nativeWork.run();
                } else {
                    nativeWork.run();
                    javaWork.run();
                }
            }

            long javaMedian = median(javaWork);
            long nativeMedian = median(nativeWork);
            System.out.printf(
                Locale.ROOT,
                "paletted_container distinct=%d palette=%s longs=%d java_median_ns=%d native_median_ns=%d speedup=%.3fx%n",
                distinct,
                palette.getClass().getSimpleName(),
                raw.length,
                javaMedian,
                nativeMedian,
                (double) javaMedian / nativeMedian
            );
        } finally {
            buffer.release();
        }
    }

    private static long median(Runnable work) {
        long[] samples = new long[MEASURE_ITERATIONS];
        for (int iteration = 0; iteration < samples.length; iteration++) {
            long start = System.nanoTime();
            work.run();
            samples[iteration] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }
}
