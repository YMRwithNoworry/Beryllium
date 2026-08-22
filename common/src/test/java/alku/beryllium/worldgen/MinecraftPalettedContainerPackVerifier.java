package alku.beryllium.worldgen;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.compute.PalettedContainerPacker;
import io.netty.buffer.Unpooled;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.SingleValuePalette;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Byte-for-byte parity between the Rust paletted-container packer and vanilla
 * {@code PalettedContainer.Data.write} across every palette implementation the
 * section serializer can produce.
 */
public final class MinecraftPalettedContainerPackVerifier {
    private static final Field CONTAINER_DATA = field(PalettedContainer.class, "data");
    private static final Field DATA_STORAGE = dataField("storage");
    private static final Field DATA_PALETTE = dataField("palette");

    private MinecraftPalettedContainerPackVerifier() {
    }

    public static void verifyNativeMatchesMinecraftReference() {
        verifyKernelLayout();
        Set<Class<?>> paletteCoverage = new HashSet<>();
        verifyContainers(PalettedContainer.Strategy.SECTION_STATES, 4_096, new int[] {1, 2, 5, 40, 200, 3_000}, paletteCoverage);
        verifyContainers(PalettedContainer.Strategy.SECTION_BIOMES, 64, new int[] {1, 2, 5}, paletteCoverage);
        for (Class<?> expected : List.of(
            SingleValuePalette.class,
            LinearPalette.class,
            HashMapPalette.class,
            GlobalPalette.class
        )) {
            if (!paletteCoverage.contains(expected)) {
                throw new AssertionError("Parity coverage is missing palette implementation " + expected.getSimpleName());
            }
        }
        verifyFallbackForUnknownPalette();
        verifyFallbackForUndersizedOutput();
    }

    private static void verifyKernelLayout() {
        byte[] output = new byte[64];
        int packed = NativeBridge.packPalettedContainer(
            5,
            2,
            new int[] {3, 300, 0x20_0000},
            3,
            new long[] {0x0102_0304_0506_0708L, -1L},
            output
        );
        assertEquals(26, packed, "kernel packed length");
        assertArrayEquals(
            new byte[] {
                5,
                3,
                3,
                (byte) 0xAC, 0x02,
                (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01,
                2,
                1, 2, 3, 4, 5, 6, 7, 8,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
            },
            Arrays.copyOf(output, packed),
            "kernel wire layout"
        );

        packed = NativeBridge.packPalettedContainer(12, 0, null, 0, new long[] {42L}, output);
        assertEquals(10, packed, "global palette packed length");
        assertArrayEquals(
            new byte[] {12, 1, 0, 0, 0, 0, 0, 0, 0, 42},
            Arrays.copyOf(output, packed),
            "global palette wire layout"
        );

        packed = NativeBridge.packPalettedContainer(0, 1, new int[] {7}, 1, new long[0], output);
        assertEquals(3, packed, "single-value packed length");
        assertArrayEquals(new byte[] {0, 7, 0}, Arrays.copyOf(output, packed), "single-value wire layout");

        assertEquals(
            -1,
            NativeBridge.packPalettedContainer(5, 2, new int[] {1, 2}, 2, new long[] {1L, 2L}, new byte[8]),
            "undersized output must fall back"
        );
        assertEquals(
            -1,
            NativeBridge.packPalettedContainer(5, 9, new int[0], 0, new long[0], output),
            "unknown palette kind must fall back"
        );
    }

    private static void verifyContainers(
        PalettedContainer.Strategy strategy,
        int cellCount,
        int[] distinctCounts,
        Set<Class<?>> paletteCoverage
    ) {
        for (int distinct : distinctCounts) {
            int registrySize = Math.max(distinct * 2, 8);
            SimpleIdMap registry = new SimpleIdMap(registrySize);
            PalettedContainer<String> container = new PalettedContainer<>(
                registry,
                registry.byId(0),
                strategy
            );
            Random random = new Random(0xC0FFEEL + distinct);
            for (int index = 0; index < cellCount; index++) {
                int value = distinct == 1 ? 0 : random.nextInt(distinct);
                container.set(index & 15, (index >> 4) & 15, (index >> 8) & 15, registry.byId(value));
            }

            // vanilla write() compacts the container inside acquire()/release(); snapshot the
            // serialized form during our own acquire so it matches what Data.write sees.
            int packed;
            long[] raw;
            byte[] scratch;
            Class<?> paletteClass;
            container.acquire();
            try {
                Object data = containerData(container);
                BitStorage storage = containerStorage(data);
                Palette<?> palette = containerPalette(data);
                paletteClass = palette.getClass();
                raw = storage.getRaw();
                scratch = new byte[PalettedContainerPacker.maximumPackedSize(512, raw.length)];
                packed = PalettedContainerPacker.pack(storage, palette, raw, scratch);
            } finally {
                container.release();
            }
            paletteCoverage.add(paletteClass);
            byte[] vanilla = vanillaBytes(container);
            if (packed < 0) {
                throw new AssertionError(
                    "Native pack failed for strategy size " + strategy.size() + " distinct " + distinct
                        + " palette " + paletteClass.getSimpleName()
                );
            }
            assertEquals(
                vanilla.length,
                packed,
                "packed length for " + paletteClass.getSimpleName() + " distinct " + distinct
            );
            if (!Arrays.equals(scratch, 0, packed, vanilla, 0, vanilla.length)) {
                throw new AssertionError(
                    "Byte mismatch for " + paletteClass.getSimpleName() + " distinct " + distinct
                        + ": vanilla " + Arrays.toString(vanilla) + " native " + Arrays.toString(Arrays.copyOf(scratch, packed))
                );
            }
        }
    }

    private static void verifyFallbackForUnknownPalette() {
        Palette<String> unknown = new Palette<>() {
            @Override
            public int idFor(String value) {
                return 0;
            }

            @Override
            public boolean maybeHas(Predicate<String> predicate) {
                return false;
            }

            @Override
            public String valueFor(int id) {
                return null;
            }

            @Override
            public void read(FriendlyByteBuf buffer) {
            }

            @Override
            public void write(FriendlyByteBuf buffer) {
            }

            @Override
            public int getSerializedSize() {
                return 0;
            }

            @Override
            public int getSize() {
                return 0;
            }

            @Override
            public Palette<String> copy() {
                return this;
            }
        };
        SimpleIdMap registry = new SimpleIdMap(8);
        PalettedContainer<String> container = new PalettedContainer<>(registry, registry.byId(0), PalettedContainer.Strategy.SECTION_BIOMES);
        container.set(0, 0, 0, registry.byId(1));
        container.set(1, 0, 0, registry.byId(2));
        try {
            Object data = CONTAINER_DATA.get(container);
            BitStorage storage = (BitStorage) DATA_STORAGE.get(data);
            assertEquals(
                -1,
                PalettedContainerPacker.pack(storage, unknown, storage.getRaw(), new byte[64]),
                "custom palette implementations must stay on the vanilla path"
            );
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Unable to read PalettedContainer data fields", failure);
        }
    }

    private static void verifyFallbackForUndersizedOutput() {
        SimpleIdMap registry = new SimpleIdMap(16);
        PalettedContainer<String> container = new PalettedContainer<>(registry, registry.byId(0), PalettedContainer.Strategy.SECTION_STATES);
        Random random = new Random(0x5EEDL);
        for (int index = 0; index < 4_096; index++) {
            container.set(index & 15, (index >> 4) & 15, (index >> 8) & 15, registry.byId(random.nextInt(6)));
        }
        try {
            Object data = CONTAINER_DATA.get(container);
            BitStorage storage = (BitStorage) DATA_STORAGE.get(data);
            Palette<?> palette = (Palette<?>) DATA_PALETTE.get(data);
            long[] raw = storage.getRaw();
            assertEquals(
                -1,
                PalettedContainerPacker.pack(storage, palette, raw, new byte[raw.length * 8 - 1]),
                "undersized native output must fall back to vanilla"
            );
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Unable to read PalettedContainer data fields", failure);
        }
    }

    static byte[] vanillaBytes(PalettedContainer<String> container) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            container.write(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    static Object containerData(PalettedContainer<?> container) {
        try {
            return CONTAINER_DATA.get(container);
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Unable to read PalettedContainer data field", failure);
        }
    }

    static BitStorage containerStorage(Object data) {
        try {
            return (BitStorage) DATA_STORAGE.get(data);
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Unable to read PalettedContainer storage field", failure);
        }
    }

    static Palette<?> containerPalette(Object data) {
        try {
            return (Palette<?>) DATA_PALETTE.get(data);
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Unable to read PalettedContainer palette field", failure);
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException failure) {
            throw new AssertionError("Missing field " + owner.getSimpleName() + "." + name, failure);
        }
    }

    private static Field dataField(String name) {
        try {
            Field field = Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data").getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ClassNotFoundException | NoSuchFieldException failure) {
            throw new AssertionError("Missing PalettedContainer$Data." + name, failure);
        }
    }

    static final class SimpleIdMap implements IdMap<String> {
        private final List<String> values;
        private final java.util.Map<String, Integer> ids;

        SimpleIdMap(int size) {
            List<String> list = new ArrayList<>(size);
            java.util.Map<String, Integer> map = new java.util.HashMap<>(size * 2);
            for (int index = 0; index < size; index++) {
                String value = "value-" + index;
                list.add(value);
                map.put(value, index);
            }
            this.values = list;
            this.ids = map;
        }

        @Override
        public int getId(String value) {
            Integer id = this.ids.get(value);
            return id != null ? id : -1;
        }

        @Override
        public String byId(int id) {
            return this.values.get(id);
        }

        @Override
        public int size() {
            return this.values.size();
        }

        @Override
        public java.util.Iterator<String> iterator() {
            return this.values.iterator();
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + " mismatch, expected " + Arrays.toString(expected) + " but got " + Arrays.toString(actual));
        }
    }
}
